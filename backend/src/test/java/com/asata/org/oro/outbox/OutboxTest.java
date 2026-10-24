package com.asata.org.oro.outbox;

import com.asata.org.oro.PostgresIntegrationTest;
import com.asata.org.oro.context.Actor;
import com.asata.org.oro.message.entity.Message;
import com.asata.org.oro.outbox.service.ClaimedEvent;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** the outbox workers: no event taken twice at once, retried with a backoff, failed after the last attempt (3 here) */
class OutboxTest extends PostgresIntegrationTest {

    private void insertEvents(int n) {
        for (int i = 0; i < n; i++) {
            admin.update("""
                    INSERT INTO oro_outbox_event(product_id, event_type, aggregate_type, aggregate_id, payload)
                    VALUES ('ORO', 'message.created', 'message', gen_random_uuid(), '{}')""");
        }
    }

    private Map<String, Object> event(UUID id) {
        return admin.queryForMap("""
                SELECT status, attempt_count, locked_by, last_error_code, last_error_message,
                       extract(epoch FROM next_attempt_at - now()) AS wait
                FROM oro_outbox_event WHERE id = ?""", id);
    }

    @Test
    void aSentMessageIsPublishedWithItsIds() {
        UUID amani = oroUser("amani");
        UUID baraka = oroUser("baraka");
        UUID direct = conversations.openDirect(Actor.oro(amani), baraka).getId();
        Message message = say(Actor.oro(amani), direct, "Hello").message();

        assertThat(outboxWorker.runOnce("worker-1")).isEqualTo(1);

        ClaimedEvent published = outboxHandler.handled().getFirst();
        assertThat(published.eventType()).isEqualTo("message.created");
        assertThat(published.aggregateId()).isEqualTo(message.getId());
        assertThat(published.payload()).contains(direct.toString(), "\"seq\": 1").doesNotContain("Hello");
        assertThat(event(published.id())).containsEntry("status", "processed").containsEntry("locked_by", null);
        // nothing left to publish
        assertThat(outboxWorker.runOnce("worker-1")).isZero();
    }

    @Test
    void twoWorkersNeverClaimTheSameEvent() throws Exception {
        insertEvents(50);

        List<List<ClaimedEvent>> claims = concurrently(4, i -> outboxWorker.claim("worker-" + i, 30));

        Set<UUID> ids = new HashSet<>();
        int total = 0;
        for (List<ClaimedEvent> claim : claims) {
            total += claim.size();
            claim.forEach(event -> ids.add(event.id()));
        }
        assertThat(total).isEqualTo(50);
        assertThat(ids).hasSize(50);
        // all held: nothing more to claim until a lease expires
        assertThat(outboxWorker.claim("worker-9", 30)).isEmpty();
    }

    @Test
    void aFailureIsRetriedWithABackoffThenFailedWithoutItsMessage() {
        insertEvents(1);
        outboxHandler.failWith(new IllegalStateException("could not reach the gateway with body 'A secret'"));

        outboxWorker.runOnce("worker-1");
        UUID id = admin.queryForObject("SELECT id FROM oro_outbox_event", UUID.class);
        Map<String, Object> afterFirst = event(id);
        assertThat(afterFirst).containsEntry("status", "pending").containsEntry("attempt_count", 1)
                .containsEntry("last_error_code", "IllegalStateException").containsEntry("last_error_message", null);
        // base 10s, +-20%
        assertThat(((Number) afterFirst.get("wait")).doubleValue()).isBetween(7.0, 12.5);
        // not due yet
        assertThat(outboxWorker.runOnce("worker-1")).isZero();
        assertThat(event(id)).containsEntry("attempt_count", 1);

        admin.update("UPDATE oro_outbox_event SET next_attempt_at = now()");
        outboxWorker.runOnce("worker-1");
        assertThat(((Number) event(id).get("wait")).doubleValue()).isBetween(15.0, 25.0);
        admin.update("UPDATE oro_outbox_event SET next_attempt_at = now()");
        outboxWorker.runOnce("worker-1");
        assertThat(event(id)).containsEntry("status", "failed").containsEntry("attempt_count", 3);
        admin.update("UPDATE oro_outbox_event SET next_attempt_at = now()");
        assertThat(outboxWorker.claim("worker-1", 10)).isEmpty();
    }

    @Test
    void anExpiredLeaseIsTakenOverAndTheFirstWorkerCannotCompleteIt() {
        insertEvents(1);
        ClaimedEvent claimed = outboxWorker.claim("worker-1", 10).getFirst();
        assertThat(outboxWorker.claim("worker-2", 10)).isEmpty();

        // worker-1 stalls past its lease
        admin.update("UPDATE oro_outbox_event SET next_attempt_at = now() - interval '1 second'");
        ClaimedEvent retaken = outboxWorker.claim("worker-2", 10).getFirst();

        assertThat(retaken.id()).isEqualTo(claimed.id());
        assertThat(retaken.attemptCount()).isEqualTo(2);
        assertThat(outboxWorker.complete(claimed.id(), "worker-1")).isFalse();
        assertThat(outboxWorker.complete(claimed.id(), "worker-2")).isTrue();
    }

    @Test
    void theProcessedEventsAreDeletedAfterTheRetention() {
        insertEvents(2);
        outboxWorker.runOnce("worker-1");
        admin.update("""
                UPDATE oro_outbox_event SET processed_at = now() - interval '8 days'
                WHERE id = (SELECT id FROM oro_outbox_event LIMIT 1)""");
        insertEvents(1);

        assertThat(outboxWorker.purgeProcessed()).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM oro_outbox_event")).isEqualTo(2);
    }

    /** ops/outbox-replay.sh: the failed events listed, then back to pending, all or by id; nothing else touched */
    @Test
    void theReplayScriptPutsFailedEventsBackToPending() throws Exception {
        admin.update("DELETE FROM oro_outbox_event");
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID done = UUID.randomUUID();
        for (UUID id : List.of(first, second, done)) {
            admin.update("""
                    INSERT INTO oro_outbox_event(id, product_id, event_type, aggregate_type, aggregate_id, payload, status,
                                                 attempt_count, last_error_code, processed_at)
                    VALUES (?, 'ORO', 'message.created', 'message', gen_random_uuid(), '{}', ?, 10, ?, ?)""",
                    id, id == done ? "processed" : "failed", id == done ? null : "IllegalStateException",
                    id == done ? java.sql.Timestamp.from(java.time.Instant.now()) : null);
        }

        var list = replay("list");
        assertThat(list.getExitCode()).as(list.getStderr()).isZero();
        assertThat(list.getStdout()).contains(first.toString(), second.toString(), "IllegalStateException").doesNotContain(done.toString());

        var one = replay(first.toString());
        assertThat(one.getExitCode()).as(one.getStderr()).isZero();
        assertThat(one.getStdout()).contains("1 failed events back to pending");
        assertThat(event(first)).containsEntry("status", "pending").containsEntry("attempt_count", 0)
                .containsEntry("last_error_code", null);
        assertThat(event(second)).containsEntry("status", "failed");

        // anything but an id never reaches the SQL
        var injected = replay("'; DELETE FROM oro_outbox_event; --");
        assertThat(injected.getExitCode()).isEqualTo(2);
        assertThat(admin.queryForObject("SELECT count(*) FROM oro_outbox_event", Integer.class)).isEqualTo(3);

        assertThat(replay("all").getStdout()).contains("1 failed events back to pending");
        assertThat(event(second)).containsEntry("status", "pending");
        assertThat(event(done)).containsEntry("status", "processed");
    }

    private static org.testcontainers.containers.Container.ExecResult replay(String argument) throws Exception {
        return POSTGRES.execInContainer("sh", "-c", "PSQL='psql -U " + POSTGRES.getUsername() + " -d "
                + POSTGRES.getDatabaseName() + "' sh /scripts/ops/outbox-replay.sh \"$1\"", "replay", argument);
    }
}
