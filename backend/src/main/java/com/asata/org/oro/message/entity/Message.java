package com.asata.org.oro.message.entity;

import com.asata.org.oro.enums.MessageType;
import com.asata.org.oro.enums.Product;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.DynamicUpdate;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A durable message (table oro_message), number seq in its conversation (the order and the cursor of the pages).
 * clientMessageId: the idempotency key of its sender. A deleted message stays as a tombstone, its body and metadata
 * erased by the trigger oro_message_rules (and a CHECK): a deleted text cannot be read back. Only the body, the
 * metadata and the deletion can be updated, by the sender.
 */
@Getter
@Setter
@Entity
@DynamicUpdate
@Table(name = "oro_message")
public class Message {

    @Id
    private UUID id;

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private UUID conversationId;

    @Convert(converter = Product.DbConverter.class)
    @Column(name = "product_id", nullable = false, updatable = false, length = 16)
    private Product product;

    @Column(nullable = false, updatable = false)
    private long seq;

    /** null for a system message */
    @Column(name = "sender_id", updatable = false)
    private UUID senderId;

    @Column(name = "client_message_id", updatable = false, length = 64)
    private String clientMessageId;

    @Convert(converter = MessageType.DbConverter.class)
    @Column(name = "message_type", nullable = false, updatable = false, length = 16)
    private MessageType type;

    private String body;

    /** a JSON object of optional data (link previews, mentions...), never an authorization */
    @ColumnTransformer(write = "CAST(? AS jsonb)")
    @Column(nullable = false, columnDefinition = "jsonb")
    private String metadata = "{}";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** set by the trigger when the body or the metadata changes */
    @Column(name = "edited_at", insertable = false, updatable = false)
    private OffsetDateTime editedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
