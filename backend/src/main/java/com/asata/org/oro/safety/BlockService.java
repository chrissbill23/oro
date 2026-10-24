package com.asata.org.oro.safety;

import com.asata.org.oro.context.Actor;
import com.asata.org.oro.context.DbSession;
import com.asata.org.oro.enums.LeftReason;
import com.asata.org.oro.enums.Product;
import com.asata.org.oro.exception.Forbidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The blocks of Oro's people (oro_identity_block): neither opens a direct conversation with the other nor sends in
 * one, nor adds the other to a group; the blocked one's waiting request is declined. Seen by the blocker only. Umoja's
 * organizations decide who meets whom: no blocks there.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlockService {

    private final DbSession session;
    private final JdbcTemplate jdbc;

    @Transactional
    public void block(Actor actor, UUID blockedId) {
        requireOro(actor);
        session.enterUser(actor);
        if (actor.identityId().equals(blockedId)) {
            throw new IllegalArgumentException("Blocking oneself");
        }
        jdbc.update("""
                INSERT INTO oro_identity_block(blocker_id, blocked_id, product_id) VALUES (?, ?, ?)
                ON CONFLICT DO NOTHING""", actor.identityId(), blockedId, actor.product().code());
        // its request waiting for my answer: declined
        int declined = jdbc.update("""
                UPDATE oro_conversation_member m SET left_at = now(), left_reason = ?
                FROM oro_conversation c
                WHERE m.conversation_id = c.id AND m.user_id = ? AND m.left_at IS NULL AND m.pending_since IS NOT NULL
                  AND c.conversation_type = 'direct' AND c.created_by = ?""",
                LeftReason.DECLINED.code(), actor.identityId(), blockedId);
        log.info("identity {} blocked another ({} request declined)", actor.identityId(), declined);
    }

    @Transactional
    public void unblock(Actor actor, UUID blockedId) {
        requireOro(actor);
        session.enterUser(actor);
        jdbc.update("DELETE FROM oro_identity_block WHERE blocker_id = ? AND blocked_id = ?", actor.identityId(), blockedId);
    }

    /** the identities the actor blocked */
    @Transactional(readOnly = true)
    public List<UUID> blocked(Actor actor) {
        requireOro(actor);
        session.enterUser(actor);
        return jdbc.queryForList("SELECT blocked_id FROM oro_identity_block WHERE blocker_id = ? ORDER BY created_at",
                UUID.class, actor.identityId());
    }

    private static void requireOro(Actor actor) {
        if (actor.product() != Product.ORO) {
            throw new Forbidden("Your organization decides who you meet in Umoja");
        }
    }
}
