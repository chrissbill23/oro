package com.asata.org.oro.outbox.entity;

import com.asata.org.oro.enums.OutboxStatus;
import com.asata.org.oro.enums.Product;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * An event to publish (table oro_outbox_event), inserted in the transaction of its change (OutboxWriter): it exists
 * if and only if the change is committed. payload: ids only. Claimed, completed and retried by OutboxWorker
 * (oro_system) with plain SQL; the API only inserts.
 */
@Getter
@Setter
@Entity
@Table(name = "oro_outbox_event")
public class OutboxEvent {

    @Id
    private UUID id;

    @Convert(converter = Product.DbConverter.class)
    @Column(name = "product_id", nullable = false, updatable = false, length = 16)
    private Product product;

    /** e.g. message.created */
    @Column(name = "event_type", nullable = false, updatable = false, length = 64)
    private String eventType;

    @Column(name = "aggregate_type", nullable = false, updatable = false, length = 32)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @ColumnTransformer(write = "CAST(? AS jsonb)")
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private String payload;

    @Convert(converter = OutboxStatus.DbConverter.class)
    @Column(nullable = false, insertable = false, updatable = false, length = 16)
    private OutboxStatus status;

    @Column(name = "attempt_count", insertable = false, updatable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", insertable = false, updatable = false)
    private OffsetDateTime nextAttemptAt;

    @Column(name = "processed_at", insertable = false, updatable = false)
    private OffsetDateTime processedAt;

    @Column(name = "last_error_code", insertable = false, updatable = false)
    private String lastErrorCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
