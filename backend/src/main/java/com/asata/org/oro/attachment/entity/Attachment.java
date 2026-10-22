package com.asata.org.oro.attachment.entity;

import com.asata.org.oro.enums.Product;
import com.asata.org.oro.enums.ScanStatus;
import com.asata.org.oro.enums.StorageProvider;
import com.asata.org.oro.enums.UploadStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The metadata of a file of a message (table oro_attachment); the file is in an object storage, never in PostgreSQL.
 * Registered before its message (messageId null), attached when the message is sent (same conversation, same sender:
 * composite foreign key). Uploaded, checked and scanned by oro_system; downloaded only when uploaded and clean, by an
 * active member who can read its message, through a short-lived signed URL: the storage key alone gives nothing.
 */
@Getter
@Setter
@Entity
@DynamicUpdate
@Table(name = "oro_attachment")
public class Attachment {

    @Id
    private UUID id;

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private UUID conversationId;

    @Convert(converter = Product.DbConverter.class)
    @Column(name = "product_id", nullable = false, updatable = false, length = 16)
    private Product product;

    @Column(name = "message_id")
    private UUID messageId;

    @Column(name = "uploaded_by", nullable = false, updatable = false)
    private UUID uploadedBy;

    @Convert(converter = StorageProvider.DbConverter.class)
    @Column(name = "storage_provider", nullable = false, updatable = false, length = 16)
    private StorageProvider storageProvider;

    @Column(name = "storage_key", nullable = false, updatable = false, length = 512)
    private String storageKey;

    @Column(name = "file_name", nullable = false, updatable = false)
    private String fileName;

    @Column(name = "mime_type", nullable = false, updatable = false, length = 127)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "checksum_sha256", updatable = false, length = 64)
    private String checksumSha256;

    @Convert(converter = UploadStatus.DbConverter.class)
    @Column(name = "upload_status", nullable = false, updatable = false, length = 16)
    private UploadStatus uploadStatus = UploadStatus.PENDING;

    @Convert(converter = ScanStatus.DbConverter.class)
    @Column(name = "scan_status", nullable = false, updatable = false, length = 16)
    private ScanStatus scanStatus = ScanStatus.PENDING;

    @Column(name = "scanned_at", insertable = false, updatable = false)
    private OffsetDateTime scannedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "deleted_at", insertable = false, updatable = false)
    private OffsetDateTime deletedAt;

    public boolean isDownloadable() {
        return deletedAt == null && uploadStatus == UploadStatus.UPLOADED && scanStatus == ScanStatus.CLEAN;
    }
}
