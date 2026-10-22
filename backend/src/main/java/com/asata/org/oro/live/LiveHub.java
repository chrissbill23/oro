package com.asata.org.oro.live;

import com.asata.org.oro.context.Actor;
import com.asata.org.oro.enums.Product;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * The open streams (Server-Sent Events) of the users of this instance, and who is online: a user is online while one
 * of its devices has a stream open, in the scope of the stream (its product, and its organization for Umoja). The
 * events say what changed (ids, numbers): the clients read the content through the API, under its authorization.
 * <p>
 * One instance only: the streams and the presence live in its memory. Several instances need a shared bus (PostgreSQL
 * LISTEN/NOTIFY, Redis) for the events and the presence.
 */
@Slf4j
@Component
public class LiveHub {

    /** a stream stays open this long, then the client opens a new one */
    static final Duration STREAM_TIMEOUT = Duration.ofMinutes(30);

    private final Map<UUID, Set<Connection>> connections = new ConcurrentHashMap<>();

    /** an open stream: identityId and userId are the same user, as Oro and as its product know it */
    record Connection(SseEmitter emitter, UUID identityId, String userId, Scope scope) {
    }

    /** the users who see each other: a product, and an organization for Umoja */
    record Scope(Product product, UUID organizationId) {
        static Scope of(Actor actor) {
            return new Scope(actor.product(), actor.organizationId());
        }
    }

    /** a new stream of the actor: it first gets who is online, and the others of its scope learn it is */
    public SseEmitter connect(Actor actor, String userId) {
        SseEmitter emitter = newEmitter();
        Scope scope = Scope.of(actor);
        Connection connection = new Connection(emitter, actor.identityId(), userId, scope);
        boolean first = !isOnline(actor.identityId(), scope);
        connections.computeIfAbsent(actor.identityId(), id -> new CopyOnWriteArraySet<>()).add(connection);
        Runnable close = () -> disconnect(connection);
        emitter.onCompletion(close);
        emitter.onTimeout(close);
        emitter.onError(error -> close.run());
        send(connection, Map.of("type", "hello", "online", online(scope)));
        if (first) {
            presence(connection, true);
        }
        return emitter;
    }

    /** the users of the scope with a stream open (their user ids in the product) */
    public Set<String> online(Product product, UUID organizationId) {
        return online(new Scope(product, organizationId));
    }

    /** an event for these identities, on their streams of the scope */
    public void send(Collection<UUID> identityIds, Product product, UUID organizationId, Map<String, ?> event) {
        Scope scope = new Scope(product, organizationId);
        for (UUID identityId : new HashSet<>(identityIds)) {
            for (Connection connection : connections.getOrDefault(identityId, Set.of())) {
                if (connection.scope().equals(scope)) {
                    send(connection, event);
                }
            }
        }
    }

    /** a comment on every stream, so that the proxies keep them open and the dead ones are found */
    @Scheduled(fixedDelayString = "${oro.live.heartbeat:PT25S}")
    public void heartbeat() {
        connections.values().forEach(set -> set.forEach(connection -> {
            try {
                connection.emitter().send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException e) {
                connection.emitter().completeWithError(e);
            }
        }));
    }

    /** the stream of a new connection (a recording one in the tests) */
    protected SseEmitter newEmitter() {
        return new SseEmitter(STREAM_TIMEOUT.toMillis());
    }

    private Set<String> online(Scope scope) {
        Set<String> userIds = new TreeSet<>();
        connections.values().forEach(set -> set.stream()
                .filter(connection -> connection.scope().equals(scope))
                .forEach(connection -> userIds.add(connection.userId())));
        return userIds;
    }

    private boolean isOnline(UUID identityId, Scope scope) {
        return connections.getOrDefault(identityId, Set.of()).stream().anyMatch(c -> c.scope().equals(scope));
    }

    private void disconnect(Connection connection) {
        Set<Connection> set = connections.get(connection.identityId());
        if (set == null || !set.remove(connection)) {
            return;
        }
        connections.computeIfPresent(connection.identityId(), (id, rest) -> rest.isEmpty() ? null : rest);
        if (!isOnline(connection.identityId(), connection.scope())) {
            presence(connection, false);
        }
    }

    /** the others of the scope learn that the user came online, or went offline */
    private void presence(Connection of, boolean online) {
        Map<String, Object> event = Map.of("type", "presence", "userId", of.userId(), "online", online);
        connections.values().forEach(set -> set.stream()
                .filter(connection -> connection.scope().equals(of.scope()) && !connection.identityId().equals(of.identityId()))
                .forEach(connection -> send(connection, event)));
    }

    private void send(Connection connection, Map<String, ?> event) {
        try {
            connection.emitter().send(SseEmitter.event().data(event, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            log.debug("stream of {} closed: {}", connection.identityId(), e.getMessage());
            connection.emitter().completeWithError(e);
        }
    }
}
