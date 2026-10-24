package com.asata.org.oro.live;

import com.asata.org.oro.context.Actor;
import com.asata.org.oro.enums.Product;
import com.asata.org.oro.exception.ServiceUnavailable;
import com.asata.org.oro.exception.TooManyRequests;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

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
 * <p>
 * The writes never block the caller (the outbox worker, a request, the heartbeat): each stream has its queue, written
 * in order by a virtual thread of its own, so a client that reads slowly holds up only itself. A stream whose queue is
 * full (its client that far behind) is ended: the client reconnects and reads what it missed through the API.
 * <p>
 * On a stop, before the server stops taking requests (a phase above its graceful shutdown): every stream hears
 * reconnect and is closed, and a new one is refused (503) until the end, so that the server does not wait for streams
 * open 30 minutes. The clients come back to the next instance, or to this one restarted.
 */
@Slf4j
@Component
public class LiveHub implements SmartLifecycle {

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
    /** the streams open on this instance, all users together */
    private final java.util.concurrent.atomic.AtomicInteger open = new java.util.concurrent.atomic.AtomicInteger();
    /** a user's streams at most (its tabs and devices): the next is refused, never one closed for it */
    private int maxStreamsPerUser = 5;
    /** the streams of this instance at most: the next gets 503 */
    private int maxStreams = 10_000;
    /** the events waiting for one stream at most: more, and the stream is ended */
    private int queueCapacity = 500;
    /** writes the streams: a virtual thread per stream being written (a direct executor in the tests) */
    private final Executor writers;
    /** the streams ended because their client read too slowly */
    private final AtomicLong endedSlow = new AtomicLong();
    /** stopping: the streams are closed, no new one */
    private volatile boolean stopping;
    private volatile boolean started;

    @org.springframework.beans.factory.annotation.Value("${oro.live.max-streams-per-user:5}")
    void setMaxStreamsPerUser(int maxStreamsPerUser) {
        this.maxStreamsPerUser = maxStreamsPerUser;
    }

    @org.springframework.beans.factory.annotation.Value("${oro.live.max-streams:10000}")
    void setMaxStreams(int maxStreams) {
        this.maxStreams = maxStreams;
    }

    @org.springframework.beans.factory.annotation.Value("${oro.live.queue-capacity:500}")
    void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public LiveHub() {
        this(Clock.systemUTC(), Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("oro-stream-", 0).factory()));
    }

    /** a clock and an executor of the tests */
    LiveHub(Clock clock, Executor writers) {
        this.clock = clock;
        this.writers = writers;
    }

    /**
     * an open stream: identityId and userId are the same user, as Oro and as its product know it; expiresAt: the end of
     * the token that opened it
     */
    record Connection(SseEmitter emitter, UUID identityId, String userId, Scope scope, Instant expiresAt, Outgoing out) {
    }

    /** what waits to be written on one stream: events (maps), PING and END, written by one writer at a time */
    static final class Outgoing {
        final BlockingQueue<Object> queue;
        final AtomicBoolean writing = new AtomicBoolean();
        /** nothing more is queued: END is, or the stream is gone */
        boolean ended;

        Outgoing(int capacity) {
            queue = new ArrayBlockingQueue<>(capacity);
        }
    }

    private static final Object PING = new Object();
    private static final Object END = new Object();
    private static final Map<String, String> RECONNECT = Map.of("type", "reconnect");

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
    public synchronized SseEmitter connect(Actor actor, String userId, Instant tokenExpiresAt) {
        if (stopping) {
            throw new ServiceUnavailable("Oro is restarting, try again in a few seconds", 5);
        }
        // the limits first: a refused stream is never opened. Refusing the new one, not closing an old one: a closed
        // stream reconnects, and would close another, and so on
        if (connections.getOrDefault(actor.identityId(), Set.of()).size() >= maxStreamsPerUser) {
            throw new TooManyRequests("Too many streams open for this user (" + maxStreamsPerUser + ")", 30);
        }
        if (open.get() >= maxStreams) {
            log.warn("stream refused: {} streams open on this instance, the most it takes", open.get());
            throw new ServiceUnavailable("Too many streams open, try again later", 30);
        }
        Instant now = clock.instant();
        Instant expiresAt = tokenExpiresAt == null || tokenExpiresAt.isAfter(now.plus(STREAM_TIMEOUT))
                ? now.plus(STREAM_TIMEOUT) : tokenExpiresAt;
        // the timeout of the stream: a backstop, the expiry below ends it first (nothing reaches the client from a timeout)
        SseEmitter emitter = newEmitter(Duration.between(now, expiresAt).plusSeconds(10));
        Scope scope = Scope.of(actor);
        Connection connection = new Connection(emitter, actor.identityId(), userId, scope, expiresAt, new Outgoing(queueCapacity));
        boolean first = !isOnline(actor.identityId(), scope);
        connections.computeIfAbsent(actor.identityId(), id -> new CopyOnWriteArraySet<>()).add(connection);
        open.incrementAndGet();
        // the stream ended by the server (its end, a timeout) or by the client (an error of the connection)
        Runnable gone = () -> gone(connection);
        emitter.onCompletion(gone);
        emitter.onTimeout(gone);
        emitter.onError(error -> gone.run());
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

    /**
     * the streams of these identities in the scope told to reconnect and closed: their clients read again what changed;
     * the number of streams
     */
    public int reconnect(Collection<UUID> identityIds, Product product, UUID organizationId) {
        Scope scope = new Scope(product, organizationId);
        int closed = 0;
        for (UUID identityId : new HashSet<>(identityIds)) {
            for (Connection connection : connections.getOrDefault(identityId, Set.of())) {
                if (connection.scope().equals(scope)) {
                    close(connection, true);
                    closed++;
                }
            }
        }
        return closed;
    }

    /** the streams open on this instance */
    public int openStreams() {
        return open.get();
    }

    /** the streams ended since the start because their client read too slowly */
    public long streamsEndedSlow() {
        return endedSlow.get();
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

    /**
     * ends the stream: gone at once for the others (offline), completed by its writer after what is queued.
     * reconnect: tells the client to open a new stream (its token expired, Oro stops), else just ends it
     */
    private void close(Connection connection, boolean reconnect) {
        Outgoing out = connection.out();
        synchronized (out) {
            if (out.ended) {
                return;
            }
            out.ended = true;
            int needed = reconnect ? 2 : 1;
            if (out.queue.remainingCapacity() < needed) {
                // too far behind to be worth writing
                out.queue.clear();
            }
            if (reconnect) {
                out.queue.add(RECONNECT);
            }
            out.queue.add(END);
        }
        disconnect(connection);
        write(connection);
    }

    /** the stream is over (completed, timed out, broken): nothing more to write */
    private void gone(Connection connection) {
        Outgoing out = connection.out();
        synchronized (out) {
            out.ended = true;
            out.queue.clear();
        }
        disconnect(connection);
    }

    /** a comment on every stream, so that the proxies keep them open and the dead ones are found */
    @Scheduled(fixedDelayString = "${oro.live.heartbeat:PT25S}")
    public void heartbeat() {
        connections.values().forEach(set -> set.forEach(connection -> send(connection, PING)));
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

    @Override
    public void start() {
        // a start after a stop too (the test framework pauses the contexts it keeps): streams accepted again
        stopping = false;
        started = true;
    }

    /** every stream told to reconnect and closed; none opened after (connect is synchronized with it) */
    @Override
    public void stop() {
        List<Connection> open;
        synchronized (this) {
            stopping = true;
            open = connections.values().stream().flatMap(Set::stream).toList();
        }
        open.forEach(connection -> close(connection, true));
        log.info("stopping: {} streams told to reconnect", open.size());
    }

    @Override
    public boolean isRunning() {
        return started && !stopping;
    }

    /** before the graceful shutdown of the server (WebServerGracefulShutdownLifecycle), which waits for the streams */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    @PreDestroy
    void stopExpirer() {
        expirer.shutdownNow();
        if (writers instanceof ExecutorService service) {
            service.shutdown();
        }
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
        open.decrementAndGet();
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

    /** queued for the stream, never written here: the caller never waits for a client */
    private void send(Connection connection, Object item) {
        Outgoing out = connection.out();
        boolean full;
        synchronized (out) {
            if (out.ended) {
                return;
            }
            full = !out.queue.offer(item);
        }
        if (full) {
            endedSlow.incrementAndGet();
            log.info("stream of {} ended: {} events waiting, its client reads too slowly", connection.identityId(), queueCapacity);
            close(connection, false);
            return;
        }
        write(connection);
    }

    /** a writer for the stream, unless one is at work: the events of a stream are written in order, one at a time */
    private void write(Connection connection) {
        Outgoing out = connection.out();
        if (out.writing.compareAndSet(false, true)) {
            writers.execute(() -> drain(connection));
        }
    }

    /** writes what is queued; a write may block (the client reads slowly): only this stream's writer waits */
    private void drain(Connection connection) {
        Outgoing out = connection.out();
        try {
            for (Object item; (item = out.queue.poll()) != null; ) {
                if (item == END) {
                    complete(connection);
                    return;
                }
                try {
                    connection.emitter().send(item == PING ? SseEmitter.event().comment("ping")
                            : SseEmitter.event().data(item, MediaType.APPLICATION_JSON));
                } catch (IOException | IllegalStateException e) {
                    // the client is gone: a normal end, not an error of the server
                    log.debug("stream of {} closed: {}", connection.identityId(), e.getMessage());
                    gone(connection);
                    complete(connection);
                    return;
                }
            }
        } finally {
            out.writing.set(false);
        }
        // queued after the last poll and before the flag was cleared: written now
        if (!out.queue.isEmpty()) {
            write(connection);
        }
    }

    private static void complete(Connection connection) {
        try {
            connection.emitter().complete();
        } catch (IllegalStateException e) {
            log.debug("stream of {} already closed", connection.identityId());
        }
    }
}
