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
import com.asata.org.oro.outbox.service.OutboxWriter;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    }

    /** the active conversations of the actor in its context, the latest message first, with their unread counts */
    @Transactional(readOnly = true)
    public List<ConversationSummary> list(Actor actor) {
        session.enterUser(actor);
        String query = """
                SELECT new com.asata.org.oro.conversation.dto.ConversationSummary(c.id, c.type, c.name, c.organizationId,
                       c.lastMessageSeq, c.lastMessageAt, c.lastMessageSeq - m.lastReadSeq)
                FROM ConversationMember m JOIN Conversation c ON c.id = m.conversationId
                WHERE m.userId = :me AND m.leftAt IS NULL AND c.product = :product""";
        query += actor.organizationId() == null ? " AND c.organizationId IS NULL" : " AND c.organizationId = :organizationId";
        var typed = em.createQuery(query + " ORDER BY c.lastMessageAt DESC NULLS LAST, c.id", ConversationSummary.class)
                .setParameter("me", actor.identityId())
                .setParameter("product", actor.product());
        if (actor.organizationId() != null) {
            typed.setParameter("organizationId", actor.organizationId());
        }
        return typed.getResultList();
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

    private ConversationMember movePositions(Actor actor, UUID conversationId, long seq, boolean read) {
        session.enterUser(actor);
        Conversation conversation = access.requireConversation(actor, conversationId);
        ConversationMember mine = access.requireActiveMember(actor, conversation);
        if (read) {
            mine.setLastReadSeq(seq);
        }
        mine.setLastDeliveredSeq(Math.max(seq, mine.getLastDeliveredSeq()));
        em.flush();
        em.refresh(mine);
        return mine;
    }

    private Conversation requireGroup(Actor actor, UUID conversationId) {
        Conversation conversation = access.requireConversation(actor, conversationId);
        if (conversation.getType() != ConversationType.GROUP) {
            throw new Conflict("Not a group");
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
