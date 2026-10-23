package com.asata.org.oro.outbox;

import com.asata.org.oro.outbox.service.OutboxPoller;
import com.asata.org.oro.outbox.service.OutboxProperties;
import com.asata.org.oro.outbox.service.OutboxWorker;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** a stop of Oro: the batch in progress finishes, no new one is claimed (a worker of the test, no database) */
class OutboxPollerTest {

    @Test
    void aStopWaitsForTheBatchInProgressThenClaimsNothing() throws Exception {
        OutboxWorker worker = mock(OutboxWorker.class);
        CountDownLatch publishing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(worker.runOnce(anyString())).thenAnswer(call -> {
            publishing.countDown();
            release.await(5, TimeUnit.SECONDS);
            return 3;
        });
        OutboxPoller poller = new OutboxPoller(worker, new OutboxProperties());
        poller.start();

        CompletableFuture<Void> batch = CompletableFuture.runAsync(poller::poll);
        assertThat(publishing.await(5, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<Void> stop = CompletableFuture.runAsync(poller::stop);
        // the stop waits for the batch
        Thread.sleep(200);
        assertThat(stop).isNotDone();

        release.countDown();
        stop.get(5, TimeUnit.SECONDS);
        batch.get(5, TimeUnit.SECONDS);
        assertThat(poller.isRunning()).isFalse();

        poller.poll();
        poller.purge();
        verify(worker, times(1)).runOnce(anyString());
        verify(worker, never()).purgeProcessed();
    }
}
