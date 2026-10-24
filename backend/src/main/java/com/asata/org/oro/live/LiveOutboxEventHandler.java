package com.asata.org.oro.live;

import com.asata.org.oro.outbox.service.ClaimedEvent;
import com.asata.org.oro.outbox.service.OutboxEventHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;

/**
 * Publishes the outbox events on the streams of the members of their conversation (LiveHub), as ids and numbers:
 * <ul>
 * <li>{@code {type: "message", change: created | edited | deleted | reaction, conversationId, messageId, seq}}</li>
 * <li>{@code {type: "conversation", change: <the event type>, conversationId}}: created, renamed, a member added or
 * gone (the one who left or was removed hears it too, to drop the conversation)</li>
 * </ul>
 * Delivered at least once: the clients read again what changed, which is idempotent.
 * <p>
 * An event older than oro.live.event-ttl (2 minutes: the outbox was late, or the event failed and was retried or
 * replayed) is not sent: by then a client may hold newer data, and old events in a burst help nobody. Its recipients'
 * streams are told to reconnect instead, and their clients read again what changed, as on every connection
 * (oro_live_events_stale_total). A handler that keeps failing still ends failed, and alerts (OroOutboxFailed).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveOutboxEventHandler implements OutboxEventHandler {

    private final LiveEvents live;
    private final JsonMapper json;
    private final io.micrometer.core.instrument.MeterRegistry meters;
    private java.time.Duration eventTtl = java.time.Duration.ofMinutes(2);

    @org.springframework.beans.factory.annotation.Value("${oro.live.event-ttl:PT2M}")
    void setEventTtl(java.time.Duration eventTtl) {
        this.eventTtl = eventTtl;
    }

    @Override
    public void handle(ClaimedEvent event) {
        JsonNode payload = json.readTree(event.payload());
        UUID conversationId = uuid(payload, "conversationId");
        if (conversationId == null) {
            log.debug("outbox event {} {} without conversation", event.id(), event.eventType());
            return;
        }
        Map<String, Object> live = new LinkedHashMap<>();
        List<UUID> extra = new ArrayList<>();
        switch (event.eventType()) {
            case "message.created", "message.edited", "message.deleted", "reaction.changed" -> {
                live.put("type", "message");
                live.put("change", event.eventType().equals("reaction.changed") ? "reaction"
                        : event.eventType().substring("message.".length()));
                live.put("messageId", uuid(payload, "messageId"));
                live.put("seq", payload.path("seq").asLong());
            }
            default -> {
                live.put("type", "conversation");
                live.put("change", event.eventType());
                UUID member = uuid(payload, "memberId");
                if (member != null) {
                    extra.add(member);
                }
            }
        }
        live.put("conversationId", conversationId);
        java.time.Duration age = java.time.Duration.between(event.createdAt(), java.time.Instant.now());
        if (age.compareTo(eventTtl) > 0) {
            int streams = this.live.reconnectConversation(conversationId, extra);
            meters.counter("oro.live.events.stale").increment();
            log.info("outbox event {} {} is {} s old: not sent, {} streams told to reconnect", event.id(),
                    event.eventType(), age.toSeconds(), streams);
            return;
        }
        this.live.toConversation(conversationId, live, extra);
    }

    private static UUID uuid(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        return value == null || value.isNull() ? null : UUID.fromString(value.asString());
    }
}
