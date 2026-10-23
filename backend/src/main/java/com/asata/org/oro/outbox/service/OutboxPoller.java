package com.asata.org.oro.outbox.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Runs the outbox worker on a schedule (oro.outbox.enabled); several instances share the work (SKIP LOCKED).
 * <p>
 * On a stop (before the server and the database pools): no new batch is claimed, and the stop waits for the batch in
 * progress, {@link #STOP_WAIT} at most. An event claimed and not completed by then is taken again by the next worker
 * once its lease expires: published at least once, never lost.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "oro.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPoller implements SmartLifecycle {

    static final long STOP_WAIT = 10;

    /** this instance, as the holder of the events it claims */
    private final String workerId = "oro-" + UUID.randomUUID();
    private final OutboxWorker worker;
    private final OutboxProperties properties;
    /** held while a batch is published */
    private final ReentrantLock busy = new ReentrantLock();
    private volatile boolean stopping;
    private volatile boolean started;

    @Scheduled(fixedDelayString = "${oro.outbox.poll-interval:PT1S}")
    public void poll() {
        busy.lock();
        try {
            // a full batch: there may be more, go on
            while (!stopping && worker.runOnce(workerId) >= properties.getBatchSize()) {
                Thread.onSpinWait();
            }
        } finally {
            busy.unlock();
        }
    }

    @Scheduled(fixedDelayString = "${oro.outbox.purge-interval:PT1H}")
    public void purge() {
        if (!stopping) {
            worker.purgeProcessed();
        }
    }

    @Override
    public void start() {
        // a start after a stop too (the test framework pauses the contexts it keeps)
        stopping = false;
        started = true;
    }

    @Override
    public void stop() {
        stopping = true;
        try {
            if (busy.tryLock(STOP_WAIT, TimeUnit.SECONDS)) {
                busy.unlock();
                log.info("stopping: the outbox worker {} is done", workerId);
            } else {
                log.warn("stopping: the outbox batch of {} still running after {} s, its events are taken again after their lease",
                        workerId, STOP_WAIT);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return started && !stopping;
    }

    /** with the streams, before the graceful shutdown of the server */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }
}
