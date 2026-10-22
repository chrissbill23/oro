package com.asata.org.oro.attachment.service;

import com.asata.org.oro.attachment.entity.Attachment;
import com.asata.org.oro.attachment.repository.AttachmentRepository;
import com.asata.org.oro.context.Actor;
import com.asata.org.oro.context.DbSession;
import com.asata.org.oro.conversation.entity.Conversation;
import com.asata.org.oro.conversation.service.ConversationAccess;
import com.asata.org.oro.enums.ScanStatus;
import com.asata.org.oro.enums.StorageProvider;
import com.asata.org.oro.exception.Conflict;
import com.asata.org.oro.exception.NotFound;
import com.asata.org.oro.message.entity.Message;
import com.asata.org.oro.message.repository.MessageRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The metadata of the attachments. The flow: register (the API then gives a signed upload URL for the storage key),
 * the client uploads to the object storage, oro_system checks the object (markUploaded: real size, checksum) and
 * scans it (recordScan), the sender attaches it to its message (MessageService.send), the members download it
 * (requireDownloadable, then a short-lived signed URL). The storage key is random: never derived from the file name.
 */
@Service
@RequiredArgsConstructor
public class AttachmentService {

    private final DbSession session;
    private final EntityManager em;
    private final JdbcTemplate jdbc;
    private final ConversationAccess access;
    private final AttachmentRepository attachments;
    private final MessageRepository messages;

    @Value("${oro.attachments.max-size-bytes:104857600}")
    private long maxSizeBytes;

    @Value("${oro.attachments.storage-provider:s3}")
    private String storageProvider;

    /** an upload of the actor in a conversation it is an active member of */
    @Transactional
    public Attachment register(Actor actor, UUID conversationId, String fileName, String mimeType, long sizeBytes) {
        session.enterUser(actor);
        Conversation conversation = access.requireConversation(actor, conversationId);
        access.requireActiveMember(actor, conversation);
        if (sizeBytes <= 0 || sizeBytes > maxSizeBytes) {
            throw new IllegalArgumentException("The size of an attachment is between 1 byte and " + maxSizeBytes);
        }
        Attachment attachment = new Attachment();
        attachment.setId(UUID.randomUUID());
        attachment.setConversationId(conversation.getId());
        attachment.setProduct(conversation.getProduct());
        attachment.setUploadedBy(actor.identityId());
        attachment.setStorageProvider(StorageProvider.of(storageProvider));
        attachment.setStorageKey(actor.product().code().toLowerCase() + "/" + conversation.getId() + "/" + UUID.randomUUID());
        attachment.setFileName(fileName);
        attachment.setMimeType(mimeType == null ? null : mimeType.toLowerCase());
        attachment.setSizeBytes(sizeBytes);
        attachment.setCreatedAt(OffsetDateTime.now());
        em.persist(attachment);
        return attachment;
    }

    /** the attachments of a message the actor can read */
    @Transactional(readOnly = true)
    public List<Attachment> forMessage(Actor actor, UUID messageId) {
        session.enterUser(actor);
        Message message = messages.findById(messageId).orElseThrow(() -> new NotFound("Message not found"));
        Conversation conversation = access.requireConversation(actor, message.getConversationId());
        access.requireActiveMember(actor, conversation);
        return attachments.findByMessageIdOrderByCreatedAt(message.getId());
    }

    /** the attachment, if the actor may download it now (uploaded and clean); the API then signs a short-lived URL */
    @Transactional(readOnly = true)
    public Attachment requireDownloadable(Actor actor, UUID attachmentId) {
        session.enterUser(actor);
        Attachment attachment = attachments.findById(attachmentId).orElseThrow(() -> new NotFound("Attachment not found"));
        Conversation conversation = access.requireConversation(actor, attachment.getConversationId());
        access.requireActiveMember(actor, conversation);
        if (!attachment.isDownloadable()) {
            throw new Conflict("The attachment is not available");
        }
        return attachment;
    }

    /** oro_system: the object is in the storage, with its real size and checksum (checked by the storage callback) */
    @Transactional
    public void markUploaded(UUID attachmentId, long sizeBytes, String checksumSha256) {
        session.enterSystem();
        requireUpdated(jdbc.update("""
                UPDATE oro_attachment SET upload_status = 'uploaded', size_bytes = ?, checksum_sha256 = ?
                WHERE id = ? AND upload_status = 'pending'""", sizeBytes, checksumSha256, attachmentId));
    }

    /** oro_system: the result of the malware scan of an uploaded object */
    @Transactional
    public void recordScan(UUID attachmentId, ScanStatus result) {
        if (result == ScanStatus.PENDING) {
            throw new IllegalArgumentException("A scan has a result");
        }
        session.enterSystem();
        requireUpdated(jdbc.update("""
                UPDATE oro_attachment SET scan_status = ?, scanned_at = now()
                WHERE id = ? AND upload_status = 'uploaded'""", result.code(), attachmentId));
    }

    private static void requireUpdated(int updated) {
        if (updated == 0) {
            throw new NotFound("Attachment not found in this state");
        }
    }
}
