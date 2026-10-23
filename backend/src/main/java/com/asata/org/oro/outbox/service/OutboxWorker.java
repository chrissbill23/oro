package com.asata.org.oro.outbox.service;

import com.asata.org.oro.context.DbSession;
import com.asata.org.oro.enums.Product;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Publishes the outbox (role oro_system). Each step is its own short transaction, the publication is outside any:
 * <ol>
 * <li>claim: the due events (pending, or processing with an expired lease), FOR UPDATE SKIP LOCKED so that two workers
 * never take the same one; each becomes processing, held by the worker for the lease, attempt_count + 1</li>
 * <li>publish (OutboxEventHandler), at least once: a worker that dies after publishing and before completing lets the
 * lease expire and the event is published again, hence the idempotent consumers</li>
 * <li>complete (processed) or fail: pending again after an exponential backoff with jitter, failed after the last
 * attempt. Both only while the worker still holds the event (its lease may have been taken over).</li>
 * </ol>
 */
@Slf4j
@Service
public class OutboxWorker {

    private final DbSession session;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final OutboxEventHandler handler;
    private final OutboxProperties properties;

    public OutboxWorker(DbSession session, @Qualifier("systemJdbcTemplate") JdbcTemplate jdbc,
                        OutboxEventHandler handler, OutboxProperties properties) {
        this.session = session;
        this.jdbc = jdbc;
        this.tx = session.systemTransaction();
        this.handler = handler;
        this.properties = properties;
    }

    /** one round: claims a batch, publishes it; the number of events published */
    public int runOnce(String workerId) {
        int published = 0;
        for (ClaimedEvent event : claim(workerId, properties.getBatchSize())) {
            try {
                handler.handle(event);
                if (complete(event.id(), workerId)) {
                    published++;
                }
            } catch (OutboxPublishException e) {
                fail(event.id(), workerId, e.code(), e.getMessage());
            } catch (RuntimeException e) {
                // the class only: the message of an unknown exception may hold a content or a secret
                fail(event.id(), workerId, e.getClass().getSimpleName(), null);
                log.warn("outbox event {} failed, attempt {}: {}", event.id(), event.attemptCount(), e.getClass().getName());
            }
        }
        return published;
    }

    public List<ClaimedEvent> claim(String workerId, int batchSize) {
        return tx.execute(status -> {
            session.enterSystem();
            // a processing event whose lease expired after the last attempt (its workers died): failed
            jdbc.update("""
                    UPDATE oro_outbox_event SET status = 'failed', locked_by = NULL, last_error_code = 'lease_expired',
                           last_error_message = NULL
                    WHERE status = 'processing' AND next_attempt_at <= now() AND attempt_count >= ?""",
                    properties.getMaxAttempts());
            return jdbc.query("""
                    WITH due AS (
                        SELECT id FROM oro_outbox_event
                        WHERE status IN ('pending', 'processing') AND next_attempt_at <= now()
                        ORDER BY next_attempt_at
                        LIMIT ?
                        FOR UPDATE SKIP LOCKED)
                    UPDATE oro_outbox_event e SET status = 'processing', locked_by = ?, attempt_count = e.attempt_count + 1,
                           next_attempt_at = now() + make_interval(secs => ?)
                    FROM due WHERE e.id = due.id
                    RETURNING e.id, e.product_id, e.event_type, e.aggregate_type, e.aggregate_id, e.payload::text AS payload,
                              e.attempt_count""",
                    (rs, i) -> new ClaimedEvent(rs.getObject("id", UUID.class), Product.of(rs.getString("product_id")),
                            rs.getString("event_type"), rs.getString("aggregate_type"),
                            rs.getObject("aggregate_id", UUID.class), rs.getString("payload"), rs.getInt("attempt_count")),
                    batchSize, workerId, (double) properties.getLease().toMillis() / 1000);
        });
    }

    /** false when the worker no longer holds the event (lease taken over) */
    public boolean complete(UUID eventId, String workerId) {
        return Boolean.TRUE.equals(tx.execute(status -> {
            session.enterSystem();
            return jdbc.update("""
                    UPDATE oro_outbox_event SET status = 'processed', processed_at = now(), locked_by = NULL,
                           last_error_code = NULL, last_error_message = NULL
                    WHERE id = ? AND status = 'processing' AND locked_by = ?""", eventId, workerId) == 1;
        }));
    }

    /** safeMessage: stored truncated, only from an OutboxPublishException */
    public boolean fail(UUID eventId, String workerId, String errorCode, String safeMessage) {
        double base = (double) properties.getBaseBackoff().toMillis() / 1000;
        double max = (double) properties.getMaxBackoff().toMillis() / 1000;
        return Boolean.TRUE.equals(tx.execute(status -> {
            session.enterSystem();
            return jdbc.update("""
                    UPDATE oro_outbox_event SET
                           status = CASE WHEN attempt_count >= ? THEN 'failed' ELSE 'pending' END,
                           locked_by = NULL,
                           next_attempt_at = CASE WHEN attempt_count >= ? THEN next_attempt_at
                               ELSE now() + make_interval(secs => least(? * power(2, greatest(attempt_count - 1, 0)), ?)
                                                                  * (0.8 + random() * 0.4)) END,
                           last_error_code = left(?, 64), last_error_message = left(?, 500)
                    WHERE id = ? AND status = 'processing' AND locked_by = ?""",
                    properties.getMaxAttempts(), properties.getMaxAttempts(), base, max, errorCode, safeMessage,
                    eventId, workerId) == 1;
        }));
    }

    /** deletes the events processed before the retention; the number deleted */
    public int purgeProcessed() {
        Integer deleted = tx.execute(status -> {
            session.enterSystem();
            return jdbc.update("DELETE FROM oro_outbox_event WHERE status = 'processed' AND processed_at < now() - make_interval(secs => ?)",
                    (double) properties.getRetention().toMillis() / 1000);
        });
        return deleted == null ? 0 : deleted;
    }
}
