package com.asata.org.oro.identity.service;

import com.asata.org.oro.context.DbSession;
import com.asata.org.oro.enums.Product;
import com.asata.org.oro.message.service.SystemMessages;
import com.asata.org.oro.outbox.service.OutboxWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * An organization of Umoja as Umoja says it is (its trusted integration, role oro_system), in one transaction:
 * <ul>
 * <li>its members: an access each (granted again if it was revoked); the others lose theirs, and so leave the
 * conversations of the organization (trigger)</li>
 * <li>the chats of its teams: one group per team, with its name and its members (who read its whole history); the
 * members who join or leave it are told by a system message. The chat of a team that is gone keeps its history and
 * loses its members.</li>
 * </ul>
 * Idempotent: Umoja sends it on every change and periodically (reconciliation), nothing changes when nothing did.
 */
@Service
@RequiredArgsConstructor
public class OrganizationSyncService {

    private final DbSession session;
    private final JdbcTemplate jdbc;
    private final OutboxWriter outbox;
    private final SystemMessages systemMessages;

    /** a team of the organization: its id in Umoja, its name, its members (user ids of Umoja), its lead first */
    public record TeamChat(UUID teamId, String name, List<String> memberUserIds) {
    }

    /** what changed: accesses granted and revoked, chats created, members who joined and left chats */
    public record Result(int granted, int revoked, int chatsCreated, int joined, int left) {
    }

    @Transactional
    public Result sync(UUID organizationId, Collection<String> memberUserIds, Collection<TeamChat> teams) {
        session.enterSystem();
        NamedParameterJdbcTemplate named = new NamedParameterJdbcTemplate(jdbc);
        Map<String, UUID> ids = provision(named, new LinkedHashSet<>(memberUserIds));

        int granted = Arrays.stream(jdbc.batchUpdate("""
                INSERT INTO oro_organization_access(identity_id, organization_id) VALUES (?, ?)
                ON CONFLICT (identity_id, organization_id) DO UPDATE SET revoked_at = NULL, granted_at = now()
                WHERE oro_organization_access.revoked_at IS NOT NULL""",
                ids.values().stream().map(id -> new Object[]{id, organizationId}).toList())).map(n -> Math.max(n, 0)).sum();
        int revoked = ids.isEmpty()
                ? jdbc.update("UPDATE oro_organization_access SET revoked_at = now() WHERE organization_id = ? AND revoked_at IS NULL",
                organizationId)
                : named.update("""
                        UPDATE oro_organization_access SET revoked_at = now()
                        WHERE organization_id = :organization AND revoked_at IS NULL AND identity_id NOT IN (:ids)""",
                new MapSqlParameterSource("organization", organizationId).addValue("ids", ids.values()));

        int[] counts = new int[3];
        Set<UUID> teamIds = new HashSet<>();
        for (TeamChat team : teams) {
            teamIds.add(team.teamId());
            List<UUID> members = team.memberUserIds().stream().map(ids::get).filter(Objects::nonNull).distinct().toList();
            syncTeam(organizationId, team, members, counts);
        }
        // the chats of the teams that are gone: no member left
        List<UUID> gone = jdbc.queryForList("""
                SELECT id FROM oro_conversation WHERE organization_id = ? AND team_id IS NOT NULL""", UUID.class, organizationId)
                .stream().filter(conversationId -> !teamIds.contains(teamOf(conversationId))).toList();
        for (UUID conversationId : gone) {
            for (UUID member : activeMembers(conversationId)) {
                leave(conversationId, member, false);
                counts[2]++;
            }
        }
        return new Result(granted, revoked, counts[0], counts[1], counts[2]);
    }

    private void syncTeam(UUID organizationId, TeamChat team, List<UUID> members, int[] counts) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, name FROM oro_conversation WHERE organization_id = ? AND team_id = ?", organizationId, team.teamId());
        if (rows.isEmpty()) {
            if (members.isEmpty()) {
                return;
            }
            UUID conversationId = UUID.randomUUID();
            // its creator: the lead, first of the members
            jdbc.update("""
                    INSERT INTO oro_conversation(id, product_id, context_type, organization_id, conversation_type, name,
                                                 team_id, created_by, created_at)
                    VALUES (?, ?, 'organization', ?, 'group', ?, ?, ?, now())""",
                    conversationId, Product.UMOJA.code(), organizationId, team.name().strip(), team.teamId(), members.getFirst());
            members.forEach(member -> addMember(conversationId, member));
            outbox.add(Product.UMOJA, "conversation.created", "conversation", conversationId,
                    Map.of("conversationId", conversationId, "createdBy", members.getFirst()));
            counts[0]++;
            return;
        }
        UUID conversationId = (UUID) rows.getFirst().get("id");
        if (!team.name().strip().equals(rows.getFirst().get("name"))) {
            jdbc.update("UPDATE oro_conversation SET name = ? WHERE id = ?", team.name().strip(), conversationId);
            outbox.add(Product.UMOJA, "conversation.updated", "conversation", conversationId,
                    Map.of("conversationId", conversationId));
        }
        Set<UUID> active = new HashSet<>(activeMembers(conversationId));
        for (UUID member : members) {
            if (!active.contains(member)) {
                addMember(conversationId, member);
                outbox.add(Product.UMOJA, "member.added", "conversation", conversationId,
                        Map.of("conversationId", conversationId, "memberId", member));
                systemMessages.member(conversationId, Product.UMOJA, SystemMessages.JOINED, member, null);
                counts[1]++;
            }
        }
        for (UUID member : active) {
            if (!members.contains(member)) {
                leave(conversationId, member, true);
                counts[2]++;
            }
        }
    }

    private void addMember(UUID conversationId, UUID identityId) {
        jdbc.update("""
                INSERT INTO oro_conversation_member(id, conversation_id, product_id, user_id, role, shared_history)
                VALUES (?, ?, ?, ?, 'member', TRUE)
                ON CONFLICT (conversation_id, user_id) WHERE left_at IS NULL DO NOTHING""",
                UUID.randomUUID(), conversationId, Product.UMOJA.code(), identityId);
    }

    /** announce: a system message says it (not when the whole team is gone) */
    private void leave(UUID conversationId, UUID identityId, boolean announce) {
        jdbc.update("""
                UPDATE oro_conversation_member SET left_at = now(), left_reason = 'removed'
                WHERE conversation_id = ? AND user_id = ? AND left_at IS NULL""", conversationId, identityId);
        outbox.add(Product.UMOJA, "member.removed", "conversation", conversationId,
                Map.of("conversationId", conversationId, "memberId", identityId));
        if (announce) {
            systemMessages.member(conversationId, Product.UMOJA, SystemMessages.LEFT, identityId, null);
        }
    }

    private List<UUID> activeMembers(UUID conversationId) {
        return jdbc.queryForList("SELECT user_id FROM oro_conversation_member WHERE conversation_id = ? AND left_at IS NULL",
                UUID.class, conversationId);
    }

    private UUID teamOf(UUID conversationId) {
        return jdbc.queryForObject("SELECT team_id FROM oro_conversation WHERE id = ?", UUID.class, conversationId);
    }

    /** the identities of the accounts, created when missing (ON CONFLICT DO NOTHING: concurrent syncs agree) */
    private Map<String, UUID> provision(NamedParameterJdbcTemplate named, Set<String> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        jdbc.batchUpdate("""
                INSERT INTO oro_messaging_identity(id, product_id, external_user_id) VALUES (?, ?, ?)
                ON CONFLICT (product_id, external_user_id) DO NOTHING""",
                userIds.stream().map(userId -> new Object[]{UUID.randomUUID(), Product.UMOJA.code(), userId}).toList());
        Map<String, UUID> ids = new HashMap<>();
        named.query("""
                SELECT external_user_id, id FROM oro_messaging_identity
                WHERE product_id = :product AND external_user_id IN (:userIds) AND status = 'active'""",
                new MapSqlParameterSource("product", Product.UMOJA.code()).addValue("userIds", userIds),
                rs -> {
                    ids.put(rs.getString(1), rs.getObject(2, UUID.class));
                });
        return ids;
    }
}
