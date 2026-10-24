package com.asata.org.oro.outbox.service;

import com.asata.org.oro.enums.Product;

import java.time.Instant;
import java.util.UUID;

/**
 * an outbox event claimed by a worker; attemptCount includes this attempt; createdAt: when its change was committed.
 * Consumers dedupe on id.
 */
public record ClaimedEvent(UUID id, Product product, String eventType, String aggregateType, UUID aggregateId,
                           String payload, int attemptCount, Instant createdAt) {

    /** an event of now (the tests) */
    public ClaimedEvent(UUID id, Product product, String eventType, String aggregateType, UUID aggregateId,
                        String payload, int attemptCount) {
        this(id, product, eventType, aggregateType, aggregateId, payload, attemptCount, Instant.now());
    }
}
