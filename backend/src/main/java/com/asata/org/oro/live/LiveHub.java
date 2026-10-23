package com.asata.org.oro.live;

import com.asata.org.oro.context.Actor;
import com.asata.org.oro.enums.Product;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * The open streams (Server-Sent Events) of the users of this instance, and who is online: a user is online while one
 * of its devices has a stream open, in the scope of the stream (its product, and its organization for Umoja). The
 * events say what changed (ids, numbers): the clients read the content through the API, under its authorization.
 * <p>
 * A stream is as good as the token that opened it: it ends when the token expires (the client opens another with a
 * new token), and it is closed when its identity is suspended or deactivated, or loses the access to its organization
 * (LiveAuthorization). Its user is then offline for the others.
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
    /** the end of each stream at the expiry of its token, while it is open: the client hears reconnect */
    private final Map<Connection, ScheduledFuture<?>> expiries = new ConcurrentHashMap<>();
    private final ScheduledExecutorService expirer = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "oro-stream-expiry");
        thread.setDaemon(true);
        return thread;
    });
    private final Clock clock;

    public LiveHub() {
        this(Clock.systemUTC());
    }

    /** a clock of the tests */
    LiveHub(Clock clock) {
        this.clock = clock;
    }

    /**
     * an open stream: identityId and userId are the same user, as Oro and as its product know it; expiresAt: the end of
     * the token that opened it
     */
    record Connection(SseEmitter emitter, UUID identityId, String userId, Scope scope, Instant expiresAt) {
    }

    /** who has a stream open, in which scope: what LiveAuthorization checks */
    public record Subject(UUID identityId, Product product, UUID organizationId) {
    }

    /** the users who see each other: a product, and an organization for Umoja */
    record Scope(Product product, UUID organizationId) {
        static Scope of(Actor actor) {
            return new Scope(actor.product(), actor.organizationId());
        }
    }

    /**
     * a new stream of the actor, until its token expires (30 minutes at most): it first gets who is online, and the
     * others of its scope learn it is
     */
    public SseEmitter connect(Actor actor, String userId, Instant tokenExpiresAt) {
        Instant now = clock.instant();
        Instant expiresAt = tokenExpiresAt == null || tokenExpiresAt.isAfter(now.plus(STREAM_TIMEOUT))
                ? now.plus(STREAM_TIMEOUT) : tokenExpiresAt;
        // the timeout of the stream: a backstop, the expiry below ends it first (nothing reaches the client from a timeout)
        SseEmitter emitter = newEmitter(Duration.between(now, expiresAt).plusSeconds(10));
        Scope scope = Scope.of(actor);
        Connection connection = new Connection(emitter, actor.identityId(), userId, scope, expiresAt);
        boolean first = !isOnline(actor.identityId(), scope);
        connections.computeIfAbsent(actor.identityId(), id -> new CopyOnWriteArraySet<>()).add(connection);
        Runnable close = () -> disconnect(connection);
        emitter.onCompletion(close);
        emitter.onTimeout(close);
        emitter.onError(error -> close.run());
        // the token ends: the client opens a new stream, with a new token
        expiries.put(connection, expirer.schedule(() -> close(connection, true),
                Math.max(0, Duration.between(now, expiresAt).toMillis()), TimeUnit.MILLISECONDS));
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

    /** who has a stream open, in which scope */
    public Set<Subject> subjects() {
        Set<Subject> subjects = new HashSet<>();
        connections.values().forEach(set -> set.forEach(connection -> subjects.add(new Subject(
                connection.identityId(), connection.scope().product(), connection.scope().organizationId()))));
        return subjects;
    }

    /** closes the streams of the identity (suspended, deactivated); the number closed */
    public int close(UUID identityId) {
        return close(identityId, scope -> true);
    }

    /** closes the streams of the identity in the organization (its access revoked); the number closed */
    public int close(UUID identityId, UUID organizationId) {
        return close(identityId, scope -> organizationId.equals(scope.organizationId()));
    }

    /** closes the streams whose token has expired (the timeout of the stream does it too); the number closed */
    public int closeExpired() {
        Instant now = clock.instant();
        int closed = 0;
        for (Set<Connection> set : connections.values()) {
            for (Connection connection : set) {
                if (!connection.expiresAt().isAfter(now)) {
                    close(connection, true);
                    closed++;
                }
            }
        }
        return closed;
    }

    private int close(UUID identityId, java.util.function.Predicate<Scope> inScope) {
        int closed = 0;
        for (Connection connection : connections.getOrDefault(identityId, Set.of())) {
            if (inScope.test(connection.scope())) {
                close(connection, false);
                closed++;
            }
        }
        return closed;
    }

    /** reconnect: tells the client to open a new stream (its token expired), else just ends it */
    private void close(Connection connection, boolean reconnect) {
        if (reconnect) {
            send(connection, Map.of("type", "reconnect"));
        }
        try {
            connection.emitter().complete();
        } catch (IllegalStateException e) {
            log.debug("stream of {} already closed", connection.identityId());
        }
        disconnect(connection);
    }

    /** a comment on every stream, so that the proxies keep them open and the dead ones are found */
    @Scheduled(fixedDelayString = "${oro.live.heartbeat:PT25S}")
    public void heartbeat() {
        connections.values().forEach(set -> set.forEach(connection -> {
            try {
                connection.emitter().send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException e) {
                // the client is gone: a normal end, not an error of the server
                close(connection, false);
            }
        }));
    }

    /** the stream of a new connection (a recording one in the tests) */
    protected SseEmitter newEmitter(Duration timeout) {
        return new SseEmitter(timeout.toMillis());
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

    @PreDestroy
    void stop() {
        expirer.shutdownNow();
    }

    private void disconnect(Connection connection) {
        ScheduledFuture<?> expiry = expiries.remove(connection);
        if (expiry != null) {
            expiry.cancel(false);
        }
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
            close(connection, false);
        }
    }
}
