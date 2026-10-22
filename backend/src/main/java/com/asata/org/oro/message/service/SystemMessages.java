package com.asata.org.oro.message.service;

import com.asata.org.oro.context.DbSession;
import com.asata.org.oro.enums.Product;
import com.asata.org.oro.outbox.service.OutboxWriter;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;

/**
 * The system messages of a conversation: what happened in it (a member joined, left, was removed; it was renamed), in
 * its order of messages, without a sender. Written as oro_system (oro_app sends only as its identity): from the call
 * on, the rest of the transaction acts as oro_system, so it comes last, once the changes of the user are flushed.
 * <p>
 * The body is the event (a fallback); the metadata says who: {@code {"event": "member.joined", "userId": "<user id in
 * the product>", "byUserId": ...}} or {@code {"event": "conversation.renamed", "name": ...}}. The clients write the
 * sentence with the names they know.
 */
@Component
@RequiredArgsConstructor
public class SystemMessages {

    public static final String JOINED = "member.joined";
    public static final String LEFT = "member.left";
    public static final String REMOVED = "member.removed";
    public static final String RENAMED = "conversation.renamed";

    private final DbSession session;
    private final EntityManager em;
    private final JdbcTemplate jdbc;
    private final OutboxWriter outbox;
    private final JsonMapper json;

    /** a member event: who (and who did it, when it is someone else) */
    @Transactional(propagation = Propagation.MANDATORY)
    public void member(UUID conversationId, Product product, String event, UUID memberId, UUID byId) {
        enter();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("event", event);
        metadata.put("userId", externalId(memberId));
        if (byId != null && !byId.equals(memberId)) {
            metadata.put("byUserId", externalId(byId));
        }
        post(conversationId, product, event, metadata);
    }

    /** the new name of a group, and who gave it */
    @Transactional(propagation = Propagation.MANDATORY)
    public void renamed(UUID conversationId, Product product, String name, UUID byId) {
        enter();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("event", RENAMED);
        metadata.put("name", name);
        metadata.put("byUserId", externalId(byId));
        post(conversationId, product, RENAMED, metadata);
    }

    private void enter() {
        // the changes of the user first, under its own role
        em.flush();
        session.enterSystem();
    }

    private void post(UUID conversationId, Product product, String body, Map<String, Object> metadata) {
        Long seq = jdbc.queryForObject(
                "UPDATE oro_conversation SET last_message_seq = last_message_seq + 1 WHERE id = ? RETURNING last_message_seq",
                Long.class, conversationId);
        UUID messageId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO oro_message(id, conversation_id, product_id, seq, message_type, body, metadata, created_at)
                VALUES (?, ?, ?, ?, 'system', ?, ?::jsonb, now())""",
                messageId, conversationId, product.code(), seq, body, json.writeValueAsString(metadata));
        outbox.add(product, "message.created", "message", messageId,
                Map.of("conversationId", conversationId, "messageId", messageId, "seq", seq));
    }

    private String externalId(UUID identityId) {
        return jdbc.queryForObject("SELECT external_user_id FROM oro_messaging_identity WHERE id = ?", String.class, identityId);
    }
}
