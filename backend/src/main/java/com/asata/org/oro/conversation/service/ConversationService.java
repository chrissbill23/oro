package com.asata.org.oro.conversation.service;

import com.asata.org.oro.context.Actor;
import com.asata.org.oro.context.DbSession;
import com.asata.org.oro.conversation.dto.ConversationSummary;
import com.asata.org.oro.conversation.entity.Conversation;
import com.asata.org.oro.conversation.entity.ConversationMember;
import com.asata.org.oro.conversation.repository.ConversationMemberRepository;
import com.asata.org.oro.conversation.repository.ConversationRepository;
import com.asata.org.oro.enums.*;
import com.asata.org.oro.exception.Conflict;
import com.asata.org.oro.exception.Forbidden;
import com.asata.org.oro.exception.NotFound;
import com.asata.org.oro.live.LiveEvents;
import com.asata.org.oro.message.service.SystemMessages;
import com.asata.org.oro.outbox.service.OutboxWriter;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * The conversations of a user (role oro_app, under RLS). Personal for an Oro actor, in the organization of the
 * request for a Umoja actor.
 */
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final DbSession session;
    private final EntityManager em;
    private final JdbcTemplate jdbc;
    private final ConversationAccess access;
    private final ConversationRepository conversations;
    private final ConversationMemberRepository members;
    private final OutboxWriter outbox;
    private final SystemMessages systemMessages;
    private final LiveEvents live;

    /**
     * The direct conversation of the actor with another identity, created at the first call. Concurrent calls for the
     * same pair (either way round) give one conversation: the insert of the second waits on the unique index for the
     * first, does nothing, then reads its row. The pair is sorted by PostgreSQL (uuid order, which is not the order of
     * java.util.UUID). The two identities are (again) members; the other one must be allowed in the context (an
     * organization access for Umoja), else the trigger refuses it.
     */
    @Transactional
    public Conversation openDirect(Actor actor, UUID otherIdentityId) {
        session.enterUser(actor);
        UUID me = actor.identityId();
        if (me.equals(otherIdentityId)) {
            throw new Conflict("A direct conversation is between two identities");
        }
        access.requireActiveIdentity(otherIdentityId);
        jdbc.update("""
                INSERT INTO oro_conversation(id, product_id, context_type, organization_id, conversation_type,
                                             direct_identity_low, direct_identity_high, created_by, created_at)
                VALUES (?, ?, ?, ?, 'direct', LEAST(?::uuid, ?::uuid), GREATEST(?::uuid, ?::uuid), ?, now())
                ON CONFLICT DO NOTHING""",
                UUID.randomUUID(), actor.product().code(), contextOf(actor).code(), actor.organizationId(),
                me, otherIdentityId, me, otherIdentityId, me);
        UUID conversationId = actor.organizationId() == null
                ? jdbc.queryForObject("""
                        SELECT id FROM oro_conversation
                        WHERE product_id = ? AND conversation_type = 'direct' AND organization_id IS NULL
                          AND direct_identity_low = LEAST(?::uuid, ?::uuid) AND direct_identity_high = GREATEST(?::uuid, ?::uuid)""",
                        UUID.class, actor.product().code(), me, otherIdentityId, me, otherIdentityId)
                : jdbc.queryForObject("""
                        SELECT id FROM oro_conversation
                        WHERE product_id = ? AND conversation_type = 'direct' AND organization_id = ?
                          AND direct_identity_low = LEAST(?::uuid, ?::uuid) AND direct_identity_high = GREATEST(?::uuid, ?::uuid)""",
                        UUID.class, actor.product().code(), actor.organizationId(), me, otherIdentityId, me, otherIdentityId);
        for (UUID identityId : List.of(me, otherIdentityId)) {
            jdbc.update("""
                    INSERT INTO oro_conversation_member(id, conversation_id, product_id, user_id, role, shared_history)
                    VALUES (?, ?, ?, ?, 'member', TRUE)
                    ON CONFLICT (conversation_id, user_id) WHERE left_at IS NULL DO NOTHING""",
                    UUID.randomUUID(), conversationId, actor.product().code(), identityId);
        }
        return access.requireConversation(actor, conversationId);
    }

    /** a group, the actor its owner; the first members see all its history (there is none yet) */
    @Transactional
    public Conversation createGroup(Actor actor, String name, String description, Collection<UUID> memberIds) {
        session.enterUser(actor);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A group has a name");
        }
        Conversation group = new Conversation();
        group.setId(UUID.randomUUID());
        group.setProduct(actor.product());
        group.setContextType(contextOf(actor));
        group.setOrganizationId(actor.organizationId());
        group.setType(ConversationType.GROUP);
        group.setName(name.strip());
        group.setDescription(description);
        group.setCreatedBy(actor.identityId());
        group.setCreatedAt(OffsetDateTime.now());
        em.persist(group);
        em.flush();
        addMemberRow(group, actor.identityId(), MemberRole.OWNER, true, null);
        for (UUID memberId : memberIds) {
            if (!memberId.equals(actor.identityId())) {
                access.requireActiveIdentity(memberId);
                addMemberRow(group, memberId, MemberRole.MEMBER, true, actor.identityId());
            }
        }
        outbox.add(actor.product(), "conversation.created", "conversation", group.getId(),
                Map.of("conversationId", group.getId(), "createdBy", actor.identityId()));
        return group;
    }

    /**
     * Adds an identity to a group, by its owner or an admin. shareHistory: the admin's choice, the member reads the
     * messages sent before it joins, or only the next ones (privacy first).
     */
    @Transactional
    public ConversationMember addMember(Actor actor, UUID conversationId, UUID identityId, boolean shareHistory) {
        session.enterUser(actor);
        Conversation group = requireGroup(actor, conversationId);
        ConversationMember mine = access.requireActiveMember(actor, group);
        if (!mine.canManageMembers()) {
            throw new Forbidden("Only the owner and the admins add members");
        }
        access.requireActiveIdentity(identityId);
        if (members.findActive(group.getId(), identityId).isPresent()) {
            throw new Conflict("Already a member");
        }
        ConversationMember member = addMemberRow(group, identityId, MemberRole.MEMBER, shareHistory, actor.identityId());
        outbox.add(actor.product(), "member.added", "conversation", group.getId(),
                Map.of("conversationId", group.getId(), "memberId", identityId, "addedBy", actor.identityId()));
        systemMessages.member(group.getId(), actor.product(), SystemMessages.JOINED, identityId);
        return member;
    }

    /** the actor leaves a group (a direct conversation cannot be left); an owner leaving passes the ownership on */
    @Transactional
    public void leave(Actor actor, UUID conversationId) {
        session.enterUser(actor);
        Conversation group = requireGroup(actor, conversationId);
        ConversationMember mine = access.requireActiveMember(actor, group);
        if (mine.getRole() == MemberRole.OWNER) {
            // before leaving: once left, RLS no longer lets the actor change the other members
            members.findActiveMembers(group.getId()).stream()
                    .filter(member -> !member.getId().equals(mine.getId()))
                    .min((a, b) -> Integer.compare(rank(a.getRole()), rank(b.getRole())))
                    .ifPresent(successor -> successor.setRole(MemberRole.OWNER));
            em.flush();
        }
        end(mine, LeftReason.LEFT);
        outbox.add(actor.product(), "member.left", "conversation", group.getId(),
                Map.of("conversationId", group.getId(), "memberId", actor.identityId()));
        systemMessages.member(group.getId(), actor.product(), SystemMessages.LEFT, actor.identityId());
    }

    /** removes a member of a group, by the owner or an admin; the owner cannot be removed, an admin only by the owner */
    @Transactional
    public void removeMember(Actor actor, UUID conversationId, UUID identityId) {
        session.enterUser(actor);
        Conversation group = requireGroup(actor, conversationId);
        ConversationMember mine = access.requireActiveMember(actor, group);
        if (!mine.canManageMembers()) {
            throw new Forbidden("Only the owner and the admins remove members");
        }
        ConversationMember member = members.findActive(group.getId(), identityId)
                .orElseThrow(() -> new NotFound("Member not found"));
        if (member.getRole() == MemberRole.OWNER
                || (member.getRole() == MemberRole.ADMIN && mine.getRole() != MemberRole.OWNER)) {
            throw new Forbidden("This member cannot be removed by the actor");
        }
        end(member, LeftReason.REMOVED);
        outbox.add(actor.product(), "member.removed", "conversation", group.getId(),
                Map.of("conversationId", group.getId(), "memberId", identityId, "removedBy", actor.identityId()));
        systemMessages.member(group.getId(), actor.product(), SystemMessages.REMOVED, identityId);
    }

    /** a conversation the actor is an active member of */
    @Transactional(readOnly = true)
    public Conversation get(Actor actor, UUID conversationId) {
        session.enterUser(actor);
        Conversation conversation = access.requireConversation(actor, conversationId);
        access.requireActiveMember(actor, conversation);
        return conversation;
    }

    /** the active members of a conversation the actor is an active member of */
    @Transactional(readOnly = true)
    public List<ConversationMember> members(Actor actor, UUID conversationId) {
        session.enterUser(actor);
        Conversation conversation = access.requireConversation(actor, conversationId);
        access.requireActiveMember(actor, conversation);
        return members.findActiveMembers(conversation.getId());
    }

    /**
     * The active conversations of the actor in its context, the latest message (or the newest conversation) first, with
     * their unread counts, the other one of a direct conversation, and the last message the actor may read (RLS hides
     * the ones before its history).
     */
    @Transactional(readOnly = true)
    public List<ConversationSummary> list(Actor actor) {
        session.enterUser(actor);
        String sql = """
                SELECT c.id, c.conversation_type, c.name, c.team_id, c.last_message_seq, c.last_message_at, c.created_at,
                       c.last_message_seq - m.last_read_seq AS unread, m.muted_until, m.role,
                       (SELECT count(*) FROM oro_conversation_member x
                        WHERE x.conversation_id = c.id AND x.left_at IS NULL) AS member_count,
                       other.external_user_id AS other_user_id,
                       lm.id AS lm_id, lm.seq AS lm_seq, sender.external_user_id AS lm_sender, lm.message_type AS lm_type,
                       left(lm.body, 200) AS lm_body,
                       CASE WHEN lm.message_type = 'system' THEN lm.metadata::text END AS lm_metadata,
                       lm.deleted_at IS NOT NULL AS lm_deleted, lm.created_at AS lm_created_at
                FROM oro_conversation_member m
                JOIN oro_conversation c ON c.id = m.conversation_id
                LEFT JOIN oro_messaging_identity other ON c.conversation_type = 'direct'
                     AND other.id = CASE WHEN c.direct_identity_low = m.user_id
                                         THEN c.direct_identity_high ELSE c.direct_identity_low END
                LEFT JOIN oro_message lm ON lm.conversation_id = c.id AND lm.seq = c.last_message_seq
                LEFT JOIN oro_messaging_identity sender ON sender.id = lm.sender_id
                WHERE m.user_id = ? AND m.left_at IS NULL AND c.product_id = ?
                """;
        List<Object> args = new ArrayList<>(List.of(actor.identityId(), actor.product().code()));
        if (actor.organizationId() == null) {
            sql += " AND c.organization_id IS NULL";
        } else {
            sql += " AND c.organization_id = ?";
            args.add(actor.organizationId());
        }
        return jdbc.query(sql + " ORDER BY coalesce(c.last_message_at, c.created_at) DESC, c.id",
                (rs, row) -> summary(rs), args.toArray());
    }

    /** the actor mutes the conversation until then (null: unmuted) */
    @Transactional
    public ConversationMember mute(Actor actor, UUID conversationId, OffsetDateTime until) {
        session.enterUser(actor);
        Conversation conversation = access.requireConversation(actor, conversationId);
        ConversationMember mine = access.requireActiveMember(actor, conversation);
        mine.setMutedUntil(until);
        em.flush();
        return mine;
    }

    /** the owner or an admin renames a group (not a team chat: its name is the team's) */
    @Transactional
    public Conversation rename(Actor actor, UUID conversationId, String name, String description) {
        session.enterUser(actor);
        Conversation group = requireGroup(actor, conversationId);
        ConversationMember mine = access.requireActiveMember(actor, group);
        if (!mine.canManageMembers()) {
            throw new Forbidden("Only the owner and the admins rename a group");
        }
        String newName = name.strip();
        boolean renamed = !newName.equals(group.getName());
        group.setName(newName);
        group.setDescription(description == null || description.isBlank() ? null : description.strip());
        em.flush();
        outbox.add(actor.product(), "conversation.updated", "conversation", group.getId(),
                Map.of("conversationId", group.getId(), "updatedBy", actor.identityId()));
        if (renamed) {
            systemMessages.renamed(group.getId(), actor.product());
        }
        return group;
    }

    /** the actor is typing: its other devices and the other members see it at once (nothing is stored) */
    @Transactional(readOnly = true)
    public void typing(Actor actor, UUID conversationId, String userId) {
        session.enterUser(actor);
        Conversation conversation = access.requireConversation(actor, conversationId);
        access.requireActiveMember(actor, conversation);
        List<UUID> others = members.findActiveMembers(conversation.getId()).stream().map(ConversationMember::getUserId)
                .filter(id -> !id.equals(actor.identityId())).toList();
        live.afterCommit(others, actor, Map.of("type", "typing", "conversationId", conversation.getId(), "userId", userId));
    }

    private static ConversationSummary summary(ResultSet rs) throws SQLException {
        UUID lastId = rs.getObject("lm_id", UUID.class);
        ConversationSummary.LastMessage last = lastId == null ? null : new ConversationSummary.LastMessage(lastId,
                rs.getLong("lm_seq"), rs.getString("lm_sender"), MessageType.of(rs.getString("lm_type")),
                rs.getString("lm_body"), rs.getString("lm_metadata"), rs.getBoolean("lm_deleted"),
                rs.getObject("lm_created_at", OffsetDateTime.class));
        return new ConversationSummary(rs.getObject("id", UUID.class), ConversationType.of(rs.getString("conversation_type")),
                rs.getString("name"), rs.getObject("team_id", UUID.class), rs.getString("other_user_id"),
                rs.getLong("member_count"), rs.getLong("last_message_seq"),
                rs.getObject("last_message_at", OffsetDateTime.class), rs.getObject("created_at", OffsetDateTime.class),
                rs.getLong("unread"), rs.getObject("muted_until", OffsetDateTime.class), MemberRole.of(rs.getString("role")),
                last);
    }

    /** the actor has read up to seq (clamped by the database: never back, never beyond the last message) */
    @Transactional
    public ConversationMember markRead(Actor actor, UUID conversationId, long seq) {
        return movePositions(actor, conversationId, seq, true);
    }

    /** the messages up to seq reached a device of the actor */
    @Transactional
    public ConversationMember markDelivered(Actor actor, UUID conversationId, long seq) {
        return movePositions(actor, conversationId, seq, false);
    }

    /** read: the members see it at once ("Seen"), the devices of the actor too (its unread count) */
    private ConversationMember movePositions(Actor actor, UUID conversationId, long seq, boolean read) {
        session.enterUser(actor);
        Conversation conversation = access.requireConversation(actor, conversationId);
        ConversationMember mine = access.requireActiveMember(actor, conversation);
        long before = mine.getLastReadSeq();
        if (read) {
            mine.setLastReadSeq(seq);
        }
        mine.setLastDeliveredSeq(Math.max(seq, mine.getLastDeliveredSeq()));
        em.flush();
        em.refresh(mine);
        if (mine.getLastReadSeq() > before) {
            String userId = jdbc.queryForObject("SELECT external_user_id FROM oro_messaging_identity WHERE id = ?",
                    String.class, actor.identityId());
            live.afterCommit(members.findActiveMembers(conversation.getId()).stream().map(ConversationMember::getUserId).toList(),
                    actor, Map.of("type", "read", "conversationId", conversation.getId(), "userId", userId,
                            "seq", mine.getLastReadSeq()));
        }
        return mine;
    }

    /** a group the users manage: not a team chat, whose name and members are the team's (kept by Umoja) */
    private Conversation requireGroup(Actor actor, UUID conversationId) {
        Conversation conversation = access.requireConversation(actor, conversationId);
        if (conversation.getType() != ConversationType.GROUP) {
            throw new Conflict("Not a group");
        }
        if (conversation.isTeamChat()) {
            throw new Conflict("The members and the name of a team chat are the team's");
        }
        return conversation;
    }

    private ConversationMember addMemberRow(Conversation conversation, UUID identityId, MemberRole role,
                                            boolean sharedHistory, UUID addedBy) {
        ConversationMember member = new ConversationMember();
        member.setId(UUID.randomUUID());
        member.setConversationId(conversation.getId());
        member.setProduct(conversation.getProduct());
        member.setUserId(identityId);
        member.setRole(role);
        member.setSharedHistory(sharedHistory);
        member.setAddedBy(addedBy);
        em.persist(member);
        em.flush();
        // joined_seq, the positions and history_from_seq come from the database
        em.refresh(member);
        return member;
    }

    private void end(ConversationMember member, LeftReason reason) {
        member.setLeftAt(OffsetDateTime.now());
        member.setLeftReason(reason);
        em.flush();
    }

    private static ContextType contextOf(Actor actor) {
        return actor.organizationId() == null ? ContextType.PERSONAL : ContextType.ORGANIZATION;
    }

    /** the successor of an owner: an admin, else a member, the first who joined (findActiveMembers order) */
    private static int rank(MemberRole role) {
        return role == MemberRole.ADMIN ? 0 : 1;
    }
}
