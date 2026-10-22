package com.asata.org.oro.message.service;

import com.asata.org.oro.attachment.entity.Attachment;
import com.asata.org.oro.attachment.repository.AttachmentRepository;
import com.asata.org.oro.context.Actor;
import com.asata.org.oro.context.DbSession;
import com.asata.org.oro.conversation.entity.Conversation;
import com.asata.org.oro.conversation.entity.ConversationMember;
import com.asata.org.oro.conversation.service.ConversationAccess;
import com.asata.org.oro.enums.MessageType;
import com.asata.org.oro.exception.Conflict;
import com.asata.org.oro.exception.Forbidden;
import com.asata.org.oro.exception.NotFound;
import com.asata.org.oro.message.dto.MessageDTO;
import com.asata.org.oro.message.dto.MessageExtras;
import com.asata.org.oro.message.dto.MessagePage;
import com.asata.org.oro.message.dto.SendMessage;
import com.asata.org.oro.message.dto.SendResult;
import com.asata.org.oro.message.entity.Message;
import com.asata.org.oro.message.repository.MessageRepository;
import com.asata.org.oro.outbox.service.OutboxWriter;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** the messages of a user (role oro_app, under RLS) */
@Service
@RequiredArgsConstructor
public class MessageService {

    public static final int MAX_PAGE_SIZE = 100;
    /** the emojis a member may react with */
    public static final List<String> REACTIONS = List.of("👍", "❤️", "😂", "🎉", "🙌", "👀", "😮", "🙏", "✅", "🔥");
    public static final int MAX_SEARCH_RESULTS = 30;

    private final DbSession session;
    private final EntityManager em;
    private final ConversationAccess access;
    private final MessageRepository messages;
    private final AttachmentRepository attachments;
    private final OutboxWriter outbox;
    private final JdbcTemplate jdbc;

    /**
     * Sends a message, in one transaction (all or nothing): the conversation row is locked (the sends of a
     * conversation take turns, and a retry of the same request waits for the first), the idempotency key is looked up
     * (a retry returns the stored message), the conversation gives the next seq, the message, its attachments, the
     * read position of the sender and the outbox event are written, then committed together. The sender is always the
     * actor (and RLS refuses any other sender).
     */
    @Transactional
    public SendResult send(Actor actor, UUID conversationId, SendMessage request) {
        validate(request);
        session.enterUser(actor);
        Conversation conversation = access.lockConversation(actor, conversationId);
        ConversationMember mine = access.requireActiveMember(actor, conversation);
        var existing = messages.findByConversationIdAndSenderIdAndClientMessageId(conversation.getId(),
                actor.identityId(), request.clientMessageId());
        if (existing.isPresent()) {
            return new SendResult(existing.get(), true);
        }
        long seq = conversation.getLastMessageSeq() + 1;
        conversation.setLastMessageSeq(seq);
        // the seq first: the trigger of the message checks the conversation gave it
        em.flush();

        Message message = new Message();
        message.setId(UUID.randomUUID());
        message.setConversationId(conversation.getId());
        message.setProduct(conversation.getProduct());
        message.setSeq(seq);
        message.setSenderId(actor.identityId());
        message.setClientMessageId(request.clientMessageId());
        message.setType(request.type());
        message.setBody(request.body());
        message.setMetadata(request.metadata() == null ? "{}" : request.metadata());
        message.setCreatedAt(OffsetDateTime.now());
        em.persist(message);
        em.flush();

        List<UUID> attachmentIds = request.attachmentIds() == null ? List.of() : request.attachmentIds();
        for (UUID attachmentId : attachmentIds) {
            Attachment attachment = attachments.findById(attachmentId)
                    .orElseThrow(() -> new NotFound("Attachment not found"));
            if (!attachment.getConversationId().equals(conversation.getId()) || attachment.getMessageId() != null
                    || !attachment.getUploadedBy().equals(actor.identityId())) {
                throw new Conflict("The attachment is not an upload of the sender for this conversation");
            }
            attachment.setMessageId(message.getId());
        }

        mine.setLastReadSeq(seq);
        mine.setLastDeliveredSeq(seq);
        em.flush();
        outbox.add(actor.product(), "message.created", "message", message.getId(),
                Map.of("conversationId", conversation.getId(), "messageId", message.getId(), "seq", seq,
                        "senderId", actor.identityId()));
        return new SendResult(message, false);
    }

    /**
     * A page of the messages the actor may read, newest first: the newest ones (beforeSeq null), then the ones before
     * the cursor. Keyset pagination on (conversation_id, seq), never an OFFSET.
     */
    @Transactional(readOnly = true)
    public MessagePage page(Actor actor, UUID conversationId, Long beforeSeq, int size) {
        session.enterUser(actor);
        Conversation conversation = access.requireConversation(actor, conversationId);
        access.requireActiveMember(actor, conversation);
        int limit = Math.clamp(size, 1, MAX_PAGE_SIZE);
        List<Message> page = beforeSeq == null
                ? messages.findNewest(conversation.getId(), limit)
                : messages.findBefore(conversation.getId(), beforeSeq, limit);
        Long next = page.size() < limit ? null : page.getLast().getSeq();
        return new MessagePage(page, next);
    }

    /** the messages after a seq the actor has, oldest first: the catch-up of a device after a reconnection */
    @Transactional(readOnly = true)
    public List<Message> after(Actor actor, UUID conversationId, long afterSeq, int size) {
        session.enterUser(actor);
        Conversation conversation = access.requireConversation(actor, conversationId);
        access.requireActiveMember(actor, conversation);
        return messages.findAfter(conversation.getId(), afterSeq, Math.clamp(size, 1, MAX_PAGE_SIZE));
    }

    /** a message the actor may read */
    @Transactional(readOnly = true)
    public Message get(Actor actor, UUID messageId) {
        session.enterUser(actor);
        Message message = messages.findById(messageId).orElseThrow(() -> new NotFound("Message not found"));
        access.requireActiveMember(actor, access.requireConversation(actor, message.getConversationId()));
        return message;
    }

    /** the reactions and attachments of messages the actor may read (RLS shows only those) */
    @Transactional(readOnly = true)
    public MessageExtras extras(Actor actor, Collection<UUID> messageIds) {
        if (messageIds.isEmpty()) {
            return MessageExtras.NONE;
        }
        session.enterUser(actor);
        record Row(UUID messageId, String emoji, String userId) {
        }
        List<Row> rows = new NamedParameterJdbcTemplate(jdbc).query("""
                SELECT r.message_id, r.emoji, i.external_user_id FROM oro_message_reaction r
                JOIN oro_messaging_identity i ON i.id = r.user_id
                WHERE r.message_id IN (:ids) ORDER BY r.created_at, r.emoji""", Map.of("ids", messageIds),
                (rs, n) -> new Row(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)));
        Map<UUID, List<MessageDTO.ReactionDTO>> reactions = new HashMap<>();
        rows.stream().collect(Collectors.groupingBy(Row::messageId, LinkedHashMap::new, Collectors.toList()))
                .forEach((messageId, ofMessage) -> reactions.put(messageId, ofMessage.stream()
                        .collect(Collectors.groupingBy(Row::emoji, LinkedHashMap::new,
                                Collectors.mapping(Row::userId, Collectors.toList())))
                        .entrySet().stream().map(e -> new MessageDTO.ReactionDTO(e.getKey(), e.getValue())).toList()));
        Map<UUID, List<Attachment>> files = attachments.findByMessageIdInOrderByCreatedAt(messageIds).stream()
                .collect(Collectors.groupingBy(Attachment::getMessageId));
        return new MessageExtras(reactions, files);
    }

    /**
     * The actor gives (on) or takes back a reaction to a message it may read, among {@link #REACTIONS}; the members
     * see it on their streams (outbox event reaction.changed)
     */
    @Transactional
    public Message react(Actor actor, UUID messageId, String emoji, boolean on) {
        if (!REACTIONS.contains(emoji)) {
            throw new IllegalArgumentException("Not one of the reactions: " + String.join(" ", REACTIONS));
        }
        session.enterUser(actor);
        Message message = messages.findById(messageId).orElseThrow(() -> new NotFound("Message not found"));
        access.requireActiveMember(actor, access.requireConversation(actor, message.getConversationId()));
        if (message.isDeleted() || message.getType() == MessageType.SYSTEM) {
            throw new Conflict("This message takes no reaction");
        }
        int changed = on
                ? jdbc.update("""
                        INSERT INTO oro_message_reaction(message_id, conversation_id, product_id, user_id, emoji)
                        VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING""",
                message.getId(), message.getConversationId(), actor.product().code(), actor.identityId(), emoji)
                : jdbc.update("DELETE FROM oro_message_reaction WHERE message_id = ? AND user_id = ? AND emoji = ?",
                message.getId(), actor.identityId(), emoji);
        if (changed > 0) {
            outbox.add(actor.product(), "reaction.changed", "message", message.getId(),
                    Map.of("conversationId", message.getConversationId(), "messageId", message.getId(), "seq", message.getSeq()));
        }
        return message;
    }

    /**
     * The messages the actor may read that contain the text (not the system ones, not the deleted ones), in its
     * conversations of its context, or in one of them; the newest first
     */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<Message> search(Actor actor, String text, UUID conversationId) {
        String query = text == null ? "" : text.strip();
        if (query.length() < 2 || query.length() > 200) {
            throw new IllegalArgumentException("Search for 2 to 200 characters");
        }
        session.enterUser(actor);
        if (conversationId != null) {
            access.requireActiveMember(actor, access.requireConversation(actor, conversationId));
        }
        String sql = """
                SELECT msg.* FROM oro_message msg
                JOIN oro_conversation c ON c.id = msg.conversation_id
                JOIN oro_conversation_member m ON m.conversation_id = c.id AND m.user_id = :me AND m.left_at IS NULL
                WHERE c.product_id = :product AND msg.deleted_at IS NULL AND msg.message_type <> 'system'
                  AND msg.body ILIKE :pattern ESCAPE '!'""";
        sql += actor.organizationId() == null ? " AND c.organization_id IS NULL" : " AND c.organization_id = :organization";
        if (conversationId != null) {
            sql += " AND c.id = :conversation";
        }
        var found = em.createNativeQuery(sql + " ORDER BY msg.created_at DESC LIMIT " + MAX_SEARCH_RESULTS, Message.class)
                .setParameter("me", actor.identityId())
                .setParameter("product", actor.product().code())
                // the text as it is: its wildcards escaped
                .setParameter("pattern", "%" + query.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
        if (actor.organizationId() != null) {
            found.setParameter("organization", actor.organizationId());
        }
        if (conversationId != null) {
            found.setParameter("conversation", conversationId);
        }
        return found.getResultList();
    }

    /** the sender changes the text of its message (edited_at by the database) */
    @Transactional
    public Message edit(Actor actor, UUID messageId, String body) {
        session.enterUser(actor);
        Message message = requireOwnMessage(actor, messageId);
        if (message.getType() != MessageType.TEXT || body == null || body.isBlank()) {
            throw new IllegalArgumentException("Only the text of a text message is edited");
        }
        message.setBody(body);
        em.flush();
        em.refresh(message);
        outbox.add(actor.product(), "message.edited", "message", message.getId(),
                Map.of("conversationId", message.getConversationId(), "messageId", message.getId(), "seq", message.getSeq()));
        return message;
    }

    /** the sender deletes its message: a tombstone, the content erased, the attachments marked to purge */
    @Transactional
    public Message delete(Actor actor, UUID messageId) {
        session.enterUser(actor);
        Message message = requireOwnMessage(actor, messageId);
        message.setDeletedAt(OffsetDateTime.now());
        message.setDeletedBy(actor.identityId());
        message.setBody(null);
        message.setMetadata("{}");
        em.flush();
        em.refresh(message);
        outbox.add(actor.product(), "message.deleted", "message", message.getId(),
                Map.of("conversationId", message.getConversationId(), "messageId", message.getId(), "seq", message.getSeq()));
        return message;
    }

    private Message requireOwnMessage(Actor actor, UUID messageId) {
        Message message = messages.findById(messageId).orElseThrow(() -> new NotFound("Message not found"));
        Conversation conversation = access.requireConversation(actor, message.getConversationId());
        access.requireActiveMember(actor, conversation);
        if (!actor.identityId().equals(message.getSenderId())) {
            throw new Forbidden("Only its sender changes a message");
        }
        if (message.isDeleted()) {
            throw new Conflict("The message is deleted");
        }
        return message;
    }

    private static void validate(SendMessage request) {
        if (request.type() == null || request.type() == MessageType.SYSTEM) {
            throw new IllegalArgumentException("A user sends a text, an image or a file");
        }
        if (request.clientMessageId() == null || !request.clientMessageId().matches("[A-Za-z0-9._:-]{8,64}")) {
            throw new IllegalArgumentException("A send has an idempotency key");
        }
        if (request.type() == MessageType.TEXT && (request.body() == null || request.body().isBlank())) {
            throw new IllegalArgumentException("A text message has a body");
        }
    }
}
