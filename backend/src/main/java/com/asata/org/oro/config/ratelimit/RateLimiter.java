package com.asata.org.oro.config.ratelimit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Token buckets in memory, one per (caller, kind): a bucket holds up to burst tokens, refilled at the rate of its
 * limit; a request takes one, or is refused with the time until the next one. One instance only: several instances
 * each count apart (a shared counter, in PostgreSQL or the proxy, then).
 */
@Component
public class RateLimiter {

    /** a bucket full and unused this long is dropped: memory for the callers that are active only */
    private static final long IDLE_NANOS = 10L * 60 * 1_000_000_000L;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final LongSupplier nanoTime;

    public RateLimiter() {
        this(System::nanoTime);
    }

    /** a clock of the tests */
    RateLimiter(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
    }

    /** 0: allowed (a token taken); else the seconds until the caller may try again */
    public long acquire(String caller, String kind, RateLimitProperties.Limit limit) {
        Bucket bucket = buckets.computeIfAbsent(caller + "|" + kind, key -> new Bucket(limit.getBurst(), nanoTime.getAsLong()));
        return bucket.take(limit, nanoTime.getAsLong());
    }

    /** the buckets of the idle callers go */
    @Scheduled(fixedDelayString = "PT1M")
    public void evictIdle() {
        long now = nanoTime.getAsLong();
        buckets.values().removeIf(bucket -> bucket.idleSince(now) > IDLE_NANOS);
    }

    /** every bucket full again (the tests) */
    public void reset() {
        buckets.clear();
    }

    int size() {
        return buckets.size();
    }

    private static final class Bucket {
        private double tokens;
        private long refilledAt;
        private long usedAt;

        Bucket(int burst, long now) {
            this.tokens = burst;
            this.refilledAt = now;
            this.usedAt = now;
        }

        synchronized long take(RateLimitProperties.Limit limit, long now) {
            double perNano = limit.getPerMinute() / 60e9;
            tokens = Math.min(limit.getBurst(), tokens + (now - refilledAt) * perNano);
            refilledAt = now;
            usedAt = now;
            if (tokens >= 1) {
                tokens -= 1;
                return 0;
            }
            return Math.max(1, (long) Math.ceil((1 - tokens) / perNano / 1e9));
        }

        synchronized long idleSince(long now) {
            return now - usedAt;
        }
    }
}
