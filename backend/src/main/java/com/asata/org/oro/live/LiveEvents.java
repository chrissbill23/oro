package com.asata.org.oro.live;

import com.asata.org.oro.context.Actor;
import com.asata.org.oro.context.DbSession;
import com.asata.org.oro.enums.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;

/**
 * The events of the streams of the users (LiveHub): the durable changes come from the outbox (LiveOutboxEventHandler),
 * the passing ones (typing, read positions) straight from the services, after their commit.
 */
@Component
@RequiredArgsConstructor
public class LiveEvents {

    private final LiveHub hub;
    private final DbSession session;
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactionManager;

    /** after the commit of the current transaction (at once without one): the event to these identities */
    public void afterCommit(Collection<UUID> identityIds, Actor actor, Map<String, ?> event) {
        List<UUID> recipients = List.copyOf(identityIds);
        Runnable send = () -> hub.send(recipients, actor.product(), actor.organizationId(), event);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send.run();
            }
        });
    }

    /** the event to the active members of a conversation and to the identities in extra (one who just left it) */
    public void toConversation(UUID conversationId, Map<String, ?> event, Collection<UUID> extra) {
        record Target(Product product, UUID organizationId, List<UUID> members) {
        }
        Target target = new TransactionTemplate(transactionManager).execute(status -> {
            session.enterSystem();
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT product_id, organization_id FROM oro_conversation WHERE id = ?", conversationId);
            if (rows.isEmpty()) {
                return null;
            }
            List<UUID> members = jdbc.queryForList(
                    "SELECT user_id FROM oro_conversation_member WHERE conversation_id = ? AND left_at IS NULL",
                    UUID.class, conversationId);
            return new Target(Product.of((String) rows.getFirst().get("product_id")),
                    (UUID) rows.getFirst().get("organization_id"), members);
        });
        if (target == null) {
            return;
        }
        Set<UUID> recipients = new HashSet<>(target.members());
        recipients.addAll(extra);
        hub.send(recipients, target.product(), target.organizationId(), event);
    }
}
