package com.asata.org.oro.outbox.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** the default publisher until the delivery gateway exists: logs the ids of the event */
@Slf4j
@Component
public class LoggingOutboxEventHandler implements OutboxEventHandler {
    @Override
    public void handle(ClaimedEvent event) {
        log.info("outbox event {} {} {} {} attempt {}", event.id(), event.eventType(), event.aggregateType(),
                event.aggregateId(), event.attemptCount());
    }
}
