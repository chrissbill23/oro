package com.asata.org.oro.safety;

import com.asata.org.oro.context.Actor;
import com.asata.org.oro.context.DbSession;
import com.asata.org.oro.conversation.entity.Conversation;
import com.asata.org.oro.conversation.service.ConversationAccess;
import com.asata.org.oro.exception.NotFound;
import com.asata.org.oro.message.entity.Message;
import com.asata.org.oro.message.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;

/**
 * The reports of messages (oro_report): a member reports messages it can read, a request it has not answered
 * included. The server copies them (never what a client says they were): kept for review even if their sender deletes
 * them, deleted after 90 days (docs/retention.md). Read by the reviewers only (ORO-FEA-005).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    public static final Set<String> CATEGORIES = Set.of("spam", "harassment", "illegal", "other");
    static final int MAX_MESSAGES = 20;

    private final DbSession session;
    private final JdbcTemplate jdbc;
    private final ConversationAccess access;
    private final MessageRepository messages;
    private final JsonMapper json;

    @Transactional
    public UUID report(Actor actor, UUID conversationId, List<UUID> messageIds, String category, String reason) {
        if (category == null || !CATEGORIES.contains(category)) {
            throw new IllegalArgumentException("A category: spam, harassment, illegal or other");
        }
        if (reason != null && reason.length() > 1000) {
            throw new IllegalArgumentException("A reason of 1000 characters at most");
        }
        Set<UUID> ids = new LinkedHashSet<>(messageIds == null ? List.of() : messageIds);
        if (ids.isEmpty() || ids.size() > MAX_MESSAGES) {
            throw new IllegalArgumentException("1 to " + MAX_MESSAGES + " messages");
        }
        session.enterUser(actor);
        Conversation conversation = access.requireConversation(actor, conversationId);
        access.requireActiveMember(actor, conversation);
        List<Map<String, Object>> copies = new ArrayList<>();
        Set<UUID> senders = new HashSet<>();
        for (UUID id : ids) {
            // what the reporter may read only (RLS), of this conversation
            Message message = messages.findById(id).filter(m -> m.getConversationId().equals(conversationId))
                    .orElseThrow(() -> new NotFound("Message not found"));
            Map<String, Object> copy = new LinkedHashMap<>();
            copy.put("id", message.getId());
            copy.put("seq", message.getSeq());
            copy.put("senderId", message.getSenderId());
            copy.put("type", message.getType().code());
            copy.put("body", message.getBody());
            copy.put("deleted", message.isDeleted());
            copy.put("createdAt", message.getCreatedAt().toString());
            copies.add(copy);
            if (message.getSenderId() != null) {
                senders.add(message.getSenderId());
            }
        }
        UUID reported = senders.size() == 1 ? senders.iterator().next() : null;
        UUID reportId = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO oro_report(id, product_id, reporter_id, conversation_id, reported_id, category, reason, messages)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)""",
                reportId, actor.product().code(), actor.identityId(), conversationId, reported, category,
                reason == null || reason.isBlank() ? null : reason.strip(), json.writeValueAsString(copies));
        log.info("report {} by identity {}: {} message(s), {}", reportId, actor.identityId(), copies.size(), category);
        return reportId;
    }

    /** the reports past their 90 days, deleted (every hour) */
    @Scheduled(fixedDelayString = "${oro.reports.purge-interval:PT1H}", initialDelayString = "PT1M")
    public int purgeExpired() {
        Integer deleted = session.systemTransaction().execute(status -> {
            session.enterSystem();
            return session.jdbc().update("DELETE FROM oro_report WHERE expires_at < now()");
        });
        if (deleted != null && deleted > 0) {
            log.info("{} report(s) past their 90 days deleted", deleted);
        }
        return deleted == null ? 0 : deleted;
    }
}
