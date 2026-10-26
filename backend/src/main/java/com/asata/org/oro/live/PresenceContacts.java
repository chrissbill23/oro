package com.asata.org.oro.live;

import com.asata.org.oro.context.DbSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.sql.Array;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Whom an Oro person shares its presence with: the people it has a conversation with, both of them members (not left,
 * not a request or an invitation waiting for an answer), and no block between them, either way. Not all of Oro.
 * Read when its stream opens, again at once for the two people of a block or an unblock, and every 30 s for everyone
 * online (LiveAuthorization): a conversation started, accepted or left counts within 30 s. Umoja's people share theirs
 * with their organization (LiveHub).
 */
@Component
@RequiredArgsConstructor
public class PresenceContacts {

    private static final String SHARED = """
            SELECT a.user_id AS me, b.user_id AS other
            FROM oro_conversation_member a
            JOIN oro_conversation_member b ON b.conversation_id = a.conversation_id AND b.user_id <> a.user_id
                 AND b.left_at IS NULL AND b.pending_since IS NULL
            WHERE a.product_id = 'ORO' AND a.left_at IS NULL AND a.pending_since IS NULL AND a.user_id = ANY(?)
              AND NOT EXISTS (SELECT 1 FROM oro_identity_block k
                              WHERE (k.blocker_id = a.user_id AND k.blocked_id = b.user_id)
                                 OR (k.blocker_id = b.user_id AND k.blocked_id = a.user_id))""";

    private final DbSession session;
    private final LiveHub hub;

    /** the identities this one shares its presence with */
    public Set<UUID> of(UUID identityId) {
        return read(Set.of(identityId), false).getOrDefault(identityId, Set.of());
    }

    /** read again for these identities, now (a block, an unblock): each is told who appears and who goes */
    public void refresh(Collection<UUID> identityIds) {
        Set<UUID> online = new HashSet<>(identityIds);
        online.retainAll(hub.personalOnline());
        if (online.isEmpty()) {
            return;
        }
        Map<UUID, Set<UUID>> fresh = read(online, false);
        online.forEach(id -> hub.updateContacts(id, fresh.getOrDefault(id, Set.of())));
    }

    /** read again for everyone of Oro online (every 30 s): one query, the contacts online only (the others have no stream) */
    public void refreshAll() {
        Set<UUID> online = hub.personalOnline();
        if (online.isEmpty()) {
            return;
        }
        Map<UUID, Set<UUID>> fresh = read(online, true);
        online.forEach(id -> hub.updateContacts(id, fresh.getOrDefault(id, Set.of())));
    }

    private Map<UUID, Set<UUID>> read(Set<UUID> identityIds, boolean amongThemOnly) {
        Map<UUID, Set<UUID>> out = new HashMap<>();
        session.systemTransaction().executeWithoutResult(status -> {
            session.enterSystem();
            Array ids = session.jdbc().execute((java.sql.Connection connection) ->
                    connection.createArrayOf("uuid", identityIds.toArray(UUID[]::new)));
            String sql = amongThemOnly ? SHARED + " AND b.user_id = ANY(?)" : SHARED;
            Object[] args = amongThemOnly ? new Object[]{ids, ids} : new Object[]{ids};
            session.jdbc().query(sql, rs -> {
                out.computeIfAbsent(rs.getObject("me", UUID.class), id -> new HashSet<>()).add(rs.getObject("other", UUID.class));
            }, args);
        });
        return out;
    }
}
