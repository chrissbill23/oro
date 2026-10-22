package com.asata.org.oro.conversation.entity;

import com.asata.org.oro.enums.ContextType;
import com.asata.org.oro.enums.ConversationType;
import com.asata.org.oro.enums.Product;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A direct or group conversation of one product (table oro_conversation). Personal (Oro) or organization (Umoja, with
 * the id of its Umoja organization). lastMessageSeq: the number of the last message; a send takes the next one under
 * the lock of this row. Only the name, description, avatar and sequence can be updated (column grants of oro_app);
 * the rest never changes (trigger oro_conversation_rules).
 */
@Getter
@Setter
@Entity
@DynamicUpdate
@Table(name = "oro_conversation")
public class Conversation {

    @Id
    private UUID id;

    @Convert(converter = Product.DbConverter.class)
    @Column(name = "product_id", nullable = false, updatable = false, length = 16)
    private Product product;

    @Convert(converter = ContextType.DbConverter.class)
    @Column(name = "context_type", nullable = false, updatable = false, length = 16)
    private ContextType contextType;

    /** umoja_organization.id for an organization conversation, an external reference (no foreign key) */
    @Column(name = "organization_id", updatable = false)
    private UUID organizationId;

    @Convert(converter = ConversationType.DbConverter.class)
    @Column(name = "conversation_type", nullable = false, updatable = false, length = 16)
    private ConversationType type;

    /** the pair of a direct conversation, sorted by PostgreSQL (low < high as uuid) */
    @Column(name = "direct_identity_low", updatable = false)
    private UUID directIdentityLow;

    @Column(name = "direct_identity_high", updatable = false)
    private UUID directIdentityHigh;

    @Column(length = 128)
    private String name;

    @Column(length = 1024)
    private String description;

    /** key of the avatar in the object storage */
    @Column(name = "avatar_storage_key", length = 512)
    private String avatarStorageKey;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "last_message_seq", nullable = false)
    private long lastMessageSeq;

    /** set by the trigger when lastMessageSeq moves on */
    @Column(name = "last_message_at", insertable = false, updatable = false)
    private OffsetDateTime lastMessageAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
