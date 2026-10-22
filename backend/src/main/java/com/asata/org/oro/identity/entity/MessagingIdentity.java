package com.asata.org.oro.identity.entity;

import com.asata.org.oro.enums.IdentityStatus;
import com.asata.org.oro.enums.Product;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A user as Oro Messaging knows it (table oro_messaging_identity): one account of one product, (product,
 * externalUserId) unique. No email, phone, username nor credential: the products authenticate their users. Created
 * and deactivated by the trusted integrations only (IdentityService, role oro_system).
 */
@Getter
@Setter
@Entity
@DynamicUpdate
@Table(name = "oro_messaging_identity")
public class MessagingIdentity {

    @Id
    private UUID id;

    @Convert(converter = Product.DbConverter.class)
    @Column(name = "product_id", nullable = false, updatable = false, length = 16)
    private Product product;

    /** the id of the account in its product, opaque */
    @Column(name = "external_user_id", nullable = false, updatable = false, length = 128)
    private String externalUserId;

    @Convert(converter = IdentityStatus.DbConverter.class)
    @Column(nullable = false, length = 16)
    private IdentityStatus status;

    @Column(name = "deactivated_at")
    private OffsetDateTime deactivatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** set by the trigger trigger_set_updated_at_oro_messaging_identity */
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
