package com.asata.org.oro.live;

import com.asata.org.oro.context.Actor;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
        public void send(SseEventBuilder builder) {
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

        List<Object> ofType(String type) {
            return events.stream().filter(e -> type.equals(((Map<?, ?>) e).get("type"))).toList();
        }
    }

    private final List<RecordingEmitter> emitters = new ArrayList<>();
    private final LiveHub hub = new LiveHub() {
        @Override
        protected SseEmitter newEmitter() {
            RecordingEmitter emitter = new RecordingEmitter();
            emitters.add(emitter);
            return emitter;
        }
    };

    private RecordingEmitter connect(UUID identity, String userId, UUID organization) {
        return (RecordingEmitter) hub.connect(Actor.umoja(identity, organization), userId);
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
}
