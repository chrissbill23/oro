package com.asata.org.oro.outbox.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** runs the outbox worker on a schedule (oro.outbox.enabled); several instances share the work (SKIP LOCKED) */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "oro.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPoller {

    /** this instance, as the holder of the events it claims */
    private final String workerId = "oro-" + UUID.randomUUID();
    private final OutboxWorker worker;
    private final OutboxProperties properties;

    @Scheduled(fixedDelayString = "${oro.outbox.poll-interval:PT1S}")
    public void poll() {
        // a full batch: there may be more, go on
        while (worker.runOnce(workerId) >= properties.getBatchSize()) {
            Thread.onSpinWait();
        }
    }

    @Scheduled(fixedDelayString = "${oro.outbox.purge-interval:PT1H}")
    public void purge() {
        worker.purgeProcessed();
    }
}
