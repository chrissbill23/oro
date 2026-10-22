package com.asata.org.oro.attachment.repository;

import com.asata.org.oro.attachment.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** under RLS for oro_app: the attachments of the messages the identity can read, and its own uploads */
@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    /** served by idx_oro_attachment_message */
    List<Attachment> findByMessageIdOrderByCreatedAt(UUID messageId);

    /** the attachments of the messages of a page */
    List<Attachment> findByMessageIdInOrderByCreatedAt(Collection<UUID> messageIds);
}
