package com.asata.org.oro.push;

import com.asata.org.oro.account.AccountNames;
import com.asata.org.oro.context.DbSession;
import com.asata.org.oro.live.LiveHub;
import com.asata.org.oro.outbox.service.ClaimedEvent;
import com.asata.org.oro.outbox.service.OutboxEventHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The pushes of a new message: to the members away (no stream open on Oro), never its sender, nor a muted
 * conversation, nor a request or an invitation not accepted. Who and where, never the text: "New message from Amani",
 * "Design: new message from Amani"; the text stays in Oro. One topic per conversation: a newer push replaces one not
 * delivered yet. Best effort: never fails the event (the live events would be sent again for nothing); an event too
 * old (the outbox late, a replay) pushes nothing. At most once per event and device (ORO-SCL-010): each device is
 * claimed in oro_push_delivery before its push, so an event handled again (a retry, a replay) pushes nothing twice.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushOutboxEventHandler implements OutboxEventHandler {

    private static final Duration TOO_OLD = Duration.ofMinutes(10);

    private final PushProperties properties;
    private final DbSession session;
    private final LiveHub live;
    private final PushSender sender;
    private final AccountNames names;
    private final JsonMapper json;

    private record Target(UUID identityId, PushSender.Subscription subscription) {
    }

    private record Context(String type, String name, String senderUserId, List<Target> targets) {
    }

    @Override
    public void handle(ClaimedEvent event) {
        if (!properties.isEnabled() || !"message.created".equals(event.eventType())
                || Duration.between(event.createdAt(), Instant.now()).compareTo(TOO_OLD) > 0) {
            return;
        }
        try {
            push(event);
        } catch (RuntimeException e) {
            log.warn("pushes of event {} not sent: {}", event.id(), e.getClass().getSimpleName());
        }
    }

    private void push(ClaimedEvent event) {
        JsonNode payload = json.readTree(event.payload());
        if (payload.get("conversationId") == null || payload.get("senderId") == null) {
            return;
        }
        UUID conversationId = UUID.fromString(payload.get("conversationId").asString());
        UUID senderId = UUID.fromString(payload.get("senderId").asString());
        Context context = session.systemTransaction().execute(status -> {
            session.enterSystem();
            List<Map<String, Object>> conversation = session.jdbc().queryForList(
                    "SELECT conversation_type, name FROM oro_conversation WHERE id = ?", conversationId);
            if (conversation.isEmpty()) {
                return null;
            }
            List<String> senderUserId = session.jdbc().queryForList(
                    "SELECT external_user_id FROM oro_messaging_identity WHERE id = ?", String.class, senderId);
            List<Target> targets = session.jdbc().query("""
                            SELECT m.user_id, s.id, s.endpoint, s.p256dh, s.auth
                            FROM oro_conversation_member m
                            JOIN oro_messaging_identity i ON i.id = m.user_id AND i.status = 'active'
                            JOIN oro_push_subscription s ON s.identity_id = m.user_id
                            WHERE m.conversation_id = ? AND m.user_id <> ? AND m.left_at IS NULL AND m.pending_since IS NULL
                              AND (m.muted_until IS NULL OR m.muted_until < now())""",
                    (rs, i) -> new Target(rs.getObject("user_id", UUID.class), new PushSender.Subscription(
                            rs.getObject("id", UUID.class), rs.getString("endpoint"), rs.getString("p256dh"), rs.getString("auth"))),
                    conversationId, senderId);
            return new Context((String) conversation.getFirst().get("conversation_type"), (String) conversation.getFirst().get("name"),
                    senderUserId.isEmpty() ? null : senderUserId.getFirst(), targets);
        });
        if (context == null) {
            return;
        }
        List<Target> away = context.targets().stream().filter(target -> !live.isConnected(target.identityId())).toList();
        if (away.isEmpty()) {
            return;
        }
        String from = names.firstName(context.senderUserId());
        Map<String, Object> notification = new LinkedHashMap<>();
        if ("group".equals(context.type())) {
            notification.put("title", context.name());
            notification.put("body", from == null ? "New message" : "New message from " + from);
        } else {
            notification.put("title", from == null ? "Oro" : from);
            notification.put("body", "New message");
        }
        notification.put("conversationId", conversationId);
        notification.put("tag", conversationId);
        String text = json.writeValueAsString(notification);
        String topic = conversationId.toString().replace("-", "");
        Set<UUID> claimed = claim(event.id(), away);
        away.stream().filter(target -> claimed.contains(target.subscription().id()))
                .forEach(target -> sender.send(target.subscription(), text, topic));
    }

    /** the devices this event has not pushed to yet, now recorded as pushed (before the push: never twice, at worst none) */
    private Set<UUID> claim(UUID eventId, List<Target> targets) {
        String ids = targets.stream().map(target -> target.subscription().id().toString())
                .collect(Collectors.joining(",", "{", "}"));
        List<UUID> claimed = session.systemTransaction().execute(status -> {
            session.enterSystem();
            return session.jdbc().queryForList("""
                    INSERT INTO oro_push_delivery(event_id, subscription_id)
                    SELECT ?, id FROM unnest(?::uuid[]) AS id
                    ON CONFLICT DO NOTHING RETURNING subscription_id""", UUID.class, eventId, ids);
        });
        return claimed == null ? Set.of() : new HashSet<>(claimed);
    }

    /** the records of the pushes past an hour: an event that old pushes nothing anyway */
    @Scheduled(fixedDelayString = "${oro.push.delivery-purge-interval:PT1H}", initialDelayString = "PT2M")
    public int purgeDeliveries() {
        Integer deleted = session.systemTransaction().execute(status -> {
            session.enterSystem();
            return session.jdbc().update("DELETE FROM oro_push_delivery WHERE created_at < now() - interval '1 hour'");
        });
        return deleted == null ? 0 : deleted;
    }
}
