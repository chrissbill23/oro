package com.asata.org.oro.live;

import com.asata.org.oro.context.Actor;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/** the streams and the presence, without a server: recording streams */
class LiveHubTest {

    private static final UUID ACME = UUID.fromString("22222222-0000-4000-8000-0000000000a1");
    private static final UUID GLOBEX = UUID.fromString("22222222-0000-4000-8000-0000000000b1");

    /** a stream that keeps what is sent on it, and lets the test close it */
    static class RecordingEmitter extends SseEmitter {
        final List<Object> events = new ArrayList<>();
        Runnable onCompletion = () -> { };

        @Override
        public synchronized void send(SseEventBuilder builder) {
            builder.build().forEach(part -> {
                if (part.getData() instanceof Map<?, ?> event) {
                    events.add(event);
                }
            });
        }

        @Override
        public synchronized void onCompletion(Runnable callback) {
            onCompletion = callback;
        }

        void close() {
            onCompletion.run();
        }

        synchronized List<Object> ofType(String type) {
            return events.stream().filter(e -> type.equals(((Map<?, ?>) e).get("type"))).toList();
        }
    }

    private final List<RecordingEmitter> emitters = new ArrayList<>();
    private Instant now = Instant.parse("2026-10-23T10:00:00Z");
    private final LiveHub hub = new LiveHub(new java.time.Clock() {
        @Override
        public java.time.ZoneId getZone() {
            return java.time.ZoneOffset.UTC;
        }

        @Override
        public java.time.Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }, Runnable::run) {
        @Override
        protected SseEmitter newEmitter(java.time.Duration timeout) {
            RecordingEmitter emitter = new RecordingEmitter();
            emitters.add(emitter);
            return emitter;
        }
    };

    private RecordingEmitter connect(UUID identity, String userId, UUID organization) {
        return connect(identity, userId, organization, now.plus(java.time.Duration.ofMinutes(15)));
    }

    private RecordingEmitter connect(UUID identity, String userId, UUID organization, Instant tokenExpiresAt) {
        return (RecordingEmitter) hub.connect(Actor.umoja(identity, organization), userId, tokenExpiresAt);
    }

    @Test
    void aUserIsOnlineWhileOneOfItsStreamsIsOpenInItsOrganization() {
        UUID jordan = UUID.randomUUID();
        UUID amara = UUID.randomUUID();
        RecordingEmitter amaraStream = connect(amara, "amara", ACME);
        assertThat(amaraStream.ofType("hello")).containsExactly(Map.of("type", "hello", "online", Set.of("amara")));

        RecordingEmitter laptop = connect(jordan, "jordan", ACME);
        RecordingEmitter phone = connect(jordan, "jordan", ACME);
        // Amara hears it once, Jordan's second device changes nothing
        assertThat(amaraStream.ofType("presence")).containsExactly(Map.of("type", "presence", "userId", "jordan", "online", true));
        assertThat(hub.online(com.asata.org.oro.enums.Product.UMOJA, ACME)).containsExactly("amara", "jordan");
        // another organization sees nobody of Acme
        RecordingEmitter globex = connect(UUID.randomUUID(), "bob", GLOBEX);
        assertThat(globex.ofType("hello")).containsExactly(Map.of("type", "hello", "online", Set.of("bob")));
        assertThat(amaraStream.ofType("presence")).hasSize(1);

        laptop.close();
        assertThat(amaraStream.ofType("presence")).hasSize(1);
        phone.close();
        assertThat(amaraStream.ofType("presence")).last()
                .isEqualTo(Map.of("type", "presence", "userId", "jordan", "online", false));
        assertThat(hub.online(com.asata.org.oro.enums.Product.UMOJA, ACME)).containsExactly("amara");
    }

    @Test
    void anEventReachesTheStreamsOfItsRecipientsInItsScopeOnly() {
        UUID jordan = UUID.randomUUID();
        RecordingEmitter acme = connect(jordan, "jordan", ACME);
        RecordingEmitter globex = connect(jordan, "jordan", GLOBEX);
        RecordingEmitter other = connect(UUID.randomUUID(), "amara", ACME);

        hub.send(List.of(jordan), com.asata.org.oro.enums.Product.UMOJA, ACME, Map.of("type", "typing", "userId", "kwame"));

        assertThat(acme.ofType("typing")).hasSize(1);
        assertThat(globex.ofType("typing")).isEmpty();
        assertThat(other.ofType("typing")).isEmpty();
    }

    /** a stream ends with its token: the client is told to reconnect (with a new token) and is offline meanwhile */
    @Test
    void aStreamEndsWhenItsTokenExpires() {
        UUID jordan = UUID.randomUUID();
        RecordingEmitter amara = connect(UUID.randomUUID(), "amara", ACME);
        RecordingEmitter shortToken = connect(jordan, "jordan", ACME, now.plus(java.time.Duration.ofMinutes(2)));
        // a token longer than 30 minutes: the stream still ends after 30
        RecordingEmitter longToken = connect(UUID.randomUUID(), "kwame", ACME, now.plus(java.time.Duration.ofHours(2)));

        assertThat(hub.closeExpired()).isZero();
        now = now.plus(java.time.Duration.ofMinutes(2));
        assertThat(hub.closeExpired()).isEqualTo(1);
        assertThat(shortToken.ofType("reconnect")).hasSize(1);
        assertThat(hub.online(com.asata.org.oro.enums.Product.UMOJA, ACME)).containsExactly("amara", "kwame");
        assertThat(amara.ofType("presence")).last().isEqualTo(Map.of("type", "presence", "userId", "jordan", "online", false));

        now = now.plus(java.time.Duration.ofMinutes(28));
        assertThat(hub.closeExpired()).isEqualTo(2);
        assertThat(longToken.ofType("reconnect")).hasSize(1);
        assertThat(hub.subjects()).isEmpty();
    }

    /** closed by identity (suspended, deactivated) or by organization (access revoked): no event, no presence after */
    @Test
    void aStreamIsClosedWhenItsIdentityOrItsAccessEnds() {
        UUID jordan = UUID.randomUUID();
        RecordingEmitter amara = connect(UUID.randomUUID(), "amara", ACME);
        RecordingEmitter jordanAcme = connect(jordan, "jordan", ACME);
        RecordingEmitter jordanGlobex = connect(jordan, "jordan", GLOBEX);

        // Jordan loses Acme only
        assertThat(hub.close(jordan, ACME)).isEqualTo(1);
        assertThat(hub.online(com.asata.org.oro.enums.Product.UMOJA, ACME)).containsExactly("amara");
        assertThat(hub.online(com.asata.org.oro.enums.Product.UMOJA, GLOBEX)).containsExactly("jordan");
        int before = jordanAcme.events.size();
        hub.send(List.of(jordan), com.asata.org.oro.enums.Product.UMOJA, ACME, Map.of("type", "message"));
        assertThat(jordanAcme.events).hasSize(before);
        assertThat(jordanAcme.ofType("reconnect")).isEmpty();

        // then the identity is suspended
        assertThat(hub.close(jordan)).isEqualTo(1);
        assertThat(hub.subjects()).extracting(LiveHub.Subject::identityId).doesNotContain(jordan);
        assertThat(jordanGlobex.ofType("presence")).isEmpty();
        assertThat(amara.ofType("presence")).last().isEqualTo(Map.of("type", "presence", "userId", "jordan", "online", false));
    }

    /** five streams a user: the sixth is refused, the five keep going; the instance has its own cap */
    @Test
    void theStreamsOfAUserAndOfTheInstanceAreBounded() {
        UUID jordan = UUID.randomUUID();
        List<RecordingEmitter> tabs = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            tabs.add(connect(jordan, "jordan", ACME));
        }
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> connect(jordan, "jordan", ACME))
                .isInstanceOf(com.asata.org.oro.exception.TooManyRequests.class);
        // in another organization too: a user's streams, all of them
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> connect(jordan, "jordan", GLOBEX))
                .isInstanceOf(com.asata.org.oro.exception.TooManyRequests.class);
        assertThat(hub.openStreams()).isEqualTo(5);
        assertThat(tabs).allSatisfy(tab -> assertThat(tab.ofType("reconnect")).isEmpty());
        // one closed: one may open
        hub.close(jordan, ACME);
        assertThat(hub.openStreams()).isZero();
        connect(jordan, "jordan", ACME);

        hub.setMaxStreams(3);
        connect(UUID.randomUUID(), "amara", ACME);
        connect(UUID.randomUUID(), "kwame", ACME);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> connect(UUID.randomUUID(), "bob", GLOBEX))
                .isInstanceOf(com.asata.org.oro.exception.ServiceUnavailable.class);
        assertThat(hub.openStreams()).isEqualTo(3);
    }

    /** a stop of Oro: every stream hears reconnect and is closed, before the server stops; a new one is refused */
    @Test
    void aStopTellsEveryStreamToReconnectAndRefusesNewOnes() {
        hub.start();
        RecordingEmitter amara = connect(UUID.randomUUID(), "amara", ACME);
        RecordingEmitter kwame = connect(UUID.randomUUID(), "kwame", ACME);
        RecordingEmitter bob = connect(UUID.randomUUID(), "bob", GLOBEX);
        assertThat(hub.isRunning()).isTrue();
        // the hello first on every stream: nothing comes before what its client catches up from
        assertThat(List.of(amara, kwame, bob)).allSatisfy(stream ->
                assertThat(((Map<?, ?>) stream.events.getFirst()).get("type")).isEqualTo("hello"));
        int presences = amara.ofType("presence").size();
        int kwamePresences = kwame.ofType("presence").size();

        hub.stop();

        assertThat(amara.ofType("reconnect")).hasSize(1);
        assertThat(kwame.ofType("reconnect")).hasSize(1);
        assertThat(bob.ofType("reconnect")).hasSize(1);
        // everyone is told to reconnect: nobody is told the others went offline (under load, 5,000 streams each telling
        // the others made the stop last longer than its 45 s)
        assertThat(amara.ofType("presence")).hasSize(presences);
        assertThat(kwame.ofType("presence")).hasSize(kwamePresences);
        assertThat(hub.openStreams()).isZero();
        assertThat(hub.isRunning()).isFalse();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> connect(UUID.randomUUID(), "kwame", ACME))
                .isInstanceOf(com.asata.org.oro.exception.ServiceUnavailable.class);
        // before the graceful shutdown of the server, which would wait for the streams
        assertThat(hub.getPhase()).isGreaterThan(org.springframework.boot.web.server.context.WebServerGracefulShutdownLifecycle.SMART_LIFECYCLE_PHASE);
    }

    /** a stream that takes a write only when the test lets it: a client that does not read */
    static class StuckEmitter extends RecordingEmitter {
        final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        volatile boolean completed;

        @Override
        public void send(SseEventBuilder builder) {
            // outside the lock of the recording: the test reads it meanwhile
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            super.send(builder);
        }

        @Override
        public void complete() {
            completed = true;
        }
    }

    /** one client that does not read holds up nobody else, and is ended once too far behind */
    @Test
    void aClientThatDoesNotReadHoldsUpOnlyItself() throws Exception {
        List<RecordingEmitter> made = Collections.synchronizedList(new ArrayList<>());
        java.util.concurrent.ExecutorService virtual = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
        LiveHub threaded = new LiveHub(java.time.Clock.systemUTC(), virtual) {
            @Override
            protected SseEmitter newEmitter(java.time.Duration timeout) {
                RecordingEmitter emitter = made.isEmpty() ? new StuckEmitter() : new RecordingEmitter();
                made.add(emitter);
                return emitter;
            }
        };
        // a queue of 60: the reader never has more than one burst of 50 waiting, the stuck one piles up both
        threaded.setQueueCapacity(60);
        UUID stuckId = UUID.randomUUID();
        UUID amara = UUID.randomUUID();
        StuckEmitter stuck = (StuckEmitter) threaded.connect(Actor.umoja(stuckId, ACME), "stuck", null);
        RecordingEmitter reader = (RecordingEmitter) threaded.connect(Actor.umoja(amara, ACME), "amara", null);

        for (int burst = 0; burst < 2; burst++) {
            long start = System.nanoTime();
            for (int i = 0; i < 50; i++) {
                threaded.send(List.of(stuckId, amara), com.asata.org.oro.enums.Product.UMOJA, ACME,
                        Map.of("type", "typing", "n", burst * 50 + i));
            }
            // the sender never waited for the stuck client
            assertThat(java.time.Duration.ofNanos(System.nanoTime() - start)).isLessThan(java.time.Duration.ofSeconds(1));
            // the reader got the burst within a second
            int expected = (burst + 1) * 50;
            long deadline = System.currentTimeMillis() + 1000;
            while (reader.ofType("typing").size() < expected && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            assertThat(reader.ofType("typing")).hasSize(expected);
        }
        // the stuck one is ended, offline for the others, counted
        assertThat(threaded.online(com.asata.org.oro.enums.Product.UMOJA, ACME)).containsExactly("amara");
        assertThat(threaded.streamsEndedSlow()).isEqualTo(1);
        stuck.release.countDown();
        long deadline = System.currentTimeMillis() + 1000;
        while (!stuck.completed && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertThat(stuck.completed).isTrue();
        virtual.shutdownNow();
    }
}
