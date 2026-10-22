package com.asata.org.oro.outbox.service;

/**
 * Publishes an outbox event (to the WebSocket gateway, the push notifications...). Called at least once per event:
 * it must be idempotent on the event id. A failure is retried with a backoff; throw an OutboxPublishException to
 * record a safe message, any other exception records its class only.
 */
public interface OutboxEventHandler {
    void handle(ClaimedEvent event);
}
