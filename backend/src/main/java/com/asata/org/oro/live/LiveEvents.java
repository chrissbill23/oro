package com.asata.org.oro.live;

import com.asata.org.oro.context.Actor;
import com.asata.org.oro.context.DbSession;
import com.asata.org.oro.enums.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
        Target target = target(conversationId, extra);
        if (target != null) {
            hub.send(target.recipients(), target.product(), target.organizationId(), event);
        }
    }

    /**
     * instead of an event too old to be worth sending: the streams of its recipients told to reconnect, so that their
     * clients read again what changed (what a client does on every connection); the number of streams
     */
    public int reconnectConversation(UUID conversationId, Collection<UUID> extra) {
        Target target = target(conversationId, extra);
        return target == null ? 0 : hub.reconnect(target.recipients(), target.product(), target.organizationId());
    }

    private record Target(Product product, UUID organizationId, Set<UUID> recipients) {
    }

    /** who hears of a change of the conversation: its active members and extra; null when it is gone */
    private Target target(UUID conversationId, Collection<UUID> extra) {
        record Found(Product product, UUID organizationId, List<UUID> members) {
        }
        Found found = session.systemTransaction().execute(status -> {
            session.enterSystem();
            List<Map<String, Object>> rows = session.jdbc().queryForList(
                    "SELECT product_id, organization_id FROM oro_conversation WHERE id = ?", conversationId);
            if (rows.isEmpty()) {
                return null;
            }
            List<UUID> members = session.jdbc().queryForList(
                    "SELECT user_id FROM oro_conversation_member WHERE conversation_id = ? AND left_at IS NULL",
                    UUID.class, conversationId);
            return new Found(Product.of((String) rows.getFirst().get("product_id")),
                    (UUID) rows.getFirst().get("organization_id"), members);
        });
        if (found == null) {
            return null;
        }
        Set<UUID> recipients = new HashSet<>(found.members());
        recipients.addAll(extra);
        return new Target(found.product(), found.organizationId(), recipients);
    }
}
