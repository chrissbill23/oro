package com.asata.org.oro.message.service;

import com.asata.org.oro.context.DbSession;
import com.asata.org.oro.enums.Product;
import com.asata.org.oro.outbox.service.OutboxWriter;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
import java.util.UUID;

/**
 * The system messages of a conversation: what happened in it (a member joined, left, was removed; it was renamed), in
 * its order of messages, without a sender. Posted by the database function oro_post_system_message, in the transaction
 * of the change, as the user who made it (oro_app) or as the synchronization (oro_system): the function writes only a
 * system message, builds it from the rows, and refuses an event that did not happen. The API never takes oro_system.
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
    private final OutboxWriter outbox;

    /** a member event, after the change of the membership (who did it comes from the rows) */
    public void member(UUID conversationId, Product product, String event, UUID memberId) {
        post(conversationId, product, event, memberId);
    }

    /** the new name of a group, after the change, by the current user */
    public void renamed(UUID conversationId, Product product) {
        post(conversationId, product, RENAMED, null);
    }

    private void post(UUID conversationId, Product product, String event, UUID memberId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("a system message is posted in the transaction of its change");
        }
        if (!session.inSystemTransaction()) {
            // the changes of the user first: the function reads them
            em.flush();
        }
        Map<String, Object> posted = session.jdbc().queryForMap(
                "SELECT message_id, message_seq FROM oro_post_system_message(?, ?, ?)", conversationId, event, memberId);
        UUID messageId = (UUID) posted.get("message_id");
        long seq = ((Number) posted.get("message_seq")).longValue();
        outbox.add(product, "message.created", "message", messageId,
                Map.of("conversationId", conversationId, "messageId", messageId, "seq", seq));
    }
}
