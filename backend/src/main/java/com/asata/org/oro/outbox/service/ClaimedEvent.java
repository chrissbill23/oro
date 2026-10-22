package com.asata.org.oro.outbox.service;

import com.asata.org.oro.enums.Product;

import java.util.UUID;

/** an outbox event claimed by a worker; attemptCount includes this attempt. Consumers dedupe on id. */
public record ClaimedEvent(UUID id, Product product, String eventType, String aggregateType, UUID aggregateId,
                           String payload, int attemptCount) {
}
