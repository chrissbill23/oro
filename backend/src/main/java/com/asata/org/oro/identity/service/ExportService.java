package com.asata.org.oro.identity.service;

import com.asata.org.oro.context.DbSession;
import com.asata.org.oro.enums.Product;
import com.asata.org.oro.exception.NotFound;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.json.JsonMapper;

import java.io.OutputStream;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * The export of a person's data in Oro (ORO-LCH-018, the right of access): what it wrote and did, as JSON, written as
 * it is read. Its identity, its organizations (Umoja), its conversations, every message it sent (deleted ones as
 * deletions: their text is gone), its reactions, the files it sent (their names and sizes; the files themselves are in
 * their conversations), the people it blocked, the reports it made, its devices for the notifications. Not the
 * messages of the others: they are theirs (the conversations show them). The messages are read by pages of 1,000,
 * each in its own short transaction: a slow download never holds a connection of the database. For one organization
 * (Umoja: asked by it), only what is in its conversations: never the person's other organizations, nor what is not of
 * an organization (blocks, reports, devices).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportService {

    static final int PAGE = 1000;

    private final DbSession session;
    private final JsonMapper json;

    /** the identity of the person, or 404: Oro has nothing of it (never seen, or erased) */
    public UUID identity(Product product, String externalUserId) {
        return read(jdbc -> jdbc.queryForList(
                "SELECT id FROM oro_messaging_identity WHERE product_id = ? AND external_user_id = ?",
                UUID.class, product.code(), externalUserId)).stream().findFirst()
                .orElseThrow(() -> new NotFound("Oro has no data of this person"));
    }

    /** everything of the identity */
    public void write(UUID identityId, OutputStream out) {
        write(identityId, null, out);
    }

    /** organizationId: only what is in the conversations of this organization; null: everything */
    public void write(UUID identityId, UUID organizationId, OutputStream out) {
        long messages = 0;
        // the conversation c of each row in the organization, when there is one (a UUID: never text of a request)
        String inOrganization = organizationId == null ? "" : " AND c.organization_id = '" + organizationId + "'";
        try (JsonGenerator generator = json.createGenerator(out)) {
            generator.writeStartObject();
            generator.writeStringProperty("format", "oro-export/1");
            generator.writeStringProperty("exportedAt", Instant.now().toString());
            if (organizationId != null) {
                generator.writeStringProperty("organizationId", organizationId.toString());
            }
            generator.writeName("identity");
            generator.writePOJO(clean(read(jdbc -> jdbc.queryForMap("""
                    SELECT external_user_id AS "userId", product_id AS product, status, created_at AS "createdAt",
                           deactivated_at AS "deactivatedAt"
                    FROM oro_messaging_identity WHERE id = ?""", identityId))));
            section(generator, "organizations", """
                    SELECT organization_id AS "organizationId", granted_at AS "grantedAt", revoked_at AS "revokedAt"
                    FROM oro_organization_access c WHERE identity_id = ?""" + inOrganization + " ORDER BY granted_at", identityId);
            section(generator, "conversations", """
                    SELECT c.id, c.conversation_type AS type, c.name, m.role, m.joined_at AS "joinedAt", m.left_at AS "leftAt",
                           m.left_reason AS "leftReason", m.muted_until AS "mutedUntil", m.last_read_at AS "lastReadAt"
                    FROM oro_conversation_member m JOIN oro_conversation c ON c.id = m.conversation_id
                    WHERE m.user_id = ?""" + inOrganization + " ORDER BY m.joined_at", identityId);

            generator.writeName("messagesSent");
            generator.writeStartArray();
            Timestamp afterAt = new Timestamp(0);
            UUID afterId = new UUID(0, 0);
            while (true) {
                Timestamp fromAt = afterAt;
                UUID fromId = afterId;
                List<Map<String, Object>> page = read(jdbc -> jdbc.queryForList("""
                        SELECT m.id, m.conversation_id AS "conversationId", m.seq, m.message_type AS type, m.body,
                               m.metadata::text AS metadata, m.created_at AS "createdAt", m.edited_at AS "editedAt",
                               m.deleted_at AS "deletedAt"
                        FROM oro_message m JOIN oro_conversation c ON c.id = m.conversation_id
                        WHERE m.sender_id = ? AND (m.created_at, m.id) > (?, ?)""" + inOrganization + """

                        ORDER BY m.created_at, m.id LIMIT ?""", identityId, fromAt, fromId, PAGE));
                for (Map<String, Object> message : page) {
                    afterAt = (Timestamp) message.get("createdAt");
                    afterId = (UUID) message.get("id");
                    generator.writePOJO(clean(message));
                }
                messages += page.size();
                generator.flush();
                if (page.size() < PAGE) {
                    break;
                }
            }
            generator.writeEndArray();

            section(generator, "reactions", """
                    SELECT r.message_id AS "messageId", r.conversation_id AS "conversationId", r.emoji, r.created_at AS "createdAt"
                    FROM oro_message_reaction r JOIN oro_conversation c ON c.id = r.conversation_id
                    WHERE r.user_id = ?""" + inOrganization + " ORDER BY r.created_at", identityId);
            section(generator, "filesSent", """
                    SELECT a.id, a.conversation_id AS "conversationId", a.message_id AS "messageId", a.file_name AS "fileName",
                           a.mime_type AS "mimeType", a.size_bytes AS "sizeBytes", a.created_at AS "createdAt",
                           a.deleted_at AS "deletedAt"
                    FROM oro_attachment a JOIN oro_conversation c ON c.id = a.conversation_id
                    WHERE a.uploaded_by = ?""" + inOrganization + " ORDER BY a.created_at", identityId);
            // not of an organization: in the export of everything only
            if (organizationId == null) {
                section(generator, "blocked", """
                        SELECT i.external_user_id AS "userId", b.created_at AS "createdAt"
                        FROM oro_identity_block b JOIN oro_messaging_identity i ON i.id = b.blocked_id
                        WHERE b.blocker_id = ? ORDER BY b.created_at""", identityId);
                section(generator, "reportsMade", """
                        SELECT id, conversation_id AS "conversationId", category, reason, created_at AS "createdAt"
                        FROM oro_report WHERE reporter_id = ? ORDER BY created_at""", identityId);
                section(generator, "notificationDevices", """
                        SELECT user_agent AS "userAgent", created_at AS "createdAt", last_sent_at AS "lastSentAt"
                        FROM oro_push_subscription WHERE identity_id = ? ORDER BY created_at""", identityId);
            }
            generator.writeEndObject();
        }
        // the identity only: never what the export holds
        log.info("export of identity {}{}: {} message(s)", identityId,
                organizationId == null ? "" : " in organization " + organizationId, messages);
    }

    private void section(JsonGenerator generator, String name, String sql, UUID identityId) {
        generator.writeName(name);
        generator.writeStartArray();
        for (Map<String, Object> row : read(jdbc -> jdbc.queryForList(sql, identityId))) {
            generator.writePOJO(clean(row));
        }
        generator.writeEndArray();
        generator.flush();
    }

    private <T> T read(Function<org.springframework.jdbc.core.JdbcTemplate, T> query) {
        var transaction = session.systemTransaction();
        transaction.setReadOnly(true);
        return transaction.execute(status -> {
            session.enterSystem();
            return query.apply(session.jdbc());
        });
    }

    /** the values as JSON writes them: times in ISO-8601 (UTC), the JSON columns as JSON */
    private Map<String, Object> clean(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        row.forEach((key, value) -> out.put(key, switch (value) {
            case Timestamp time -> time.toInstant().toString();
            case String text when "metadata".equals(key) -> json.readTree(text);
            case UUID id -> id.toString();
            case null -> null;
            default -> value;
        }));
        return out;
    }
}
