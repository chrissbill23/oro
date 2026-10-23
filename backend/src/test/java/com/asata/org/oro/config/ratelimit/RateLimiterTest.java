package com.asata.org.oro.config.ratelimit;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/** a bucket: burst at once, then per-minute a minute; the wait until the next request; the idle buckets dropped */
class RateLimiterTest {

    private static final long SECOND = 1_000_000_000L;
    private final AtomicLong now = new AtomicLong(1000 * SECOND);
    private final RateLimiter limiter = new RateLimiter(now::get);

    private static RateLimitProperties.Limit limit(int perMinute, int burst) {
        RateLimitProperties.Limit limit = new RateLimitProperties.Limit();
        limit.setPerMinute(perMinute);
        limit.setBurst(burst);
        return limit;
    }

    @Test
    void aBurstThenTheRate() {
        RateLimitProperties.Limit sixAMinute = limit(6, 3);
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.acquire("umoja/jordan", "send", sixAMinute)).isZero();
        }
        // empty: one more every 10 s
        assertThat(limiter.acquire("umoja/jordan", "send", sixAMinute)).isEqualTo(10);
        now.addAndGet(4 * SECOND);
        assertThat(limiter.acquire("umoja/jordan", "send", sixAMinute)).isEqualTo(6);
        now.addAndGet(6 * SECOND);
        assertThat(limiter.acquire("umoja/jordan", "send", sixAMinute)).isZero();
        assertThat(limiter.acquire("umoja/jordan", "send", sixAMinute)).isPositive();
        // another caller, another kind: their own buckets
        assertThat(limiter.acquire("umoja/amara", "send", sixAMinute)).isZero();
        assertThat(limiter.acquire("umoja/jordan", "search", sixAMinute)).isZero();
        // never more than the burst, however long the wait
        now.addAndGet(3600 * SECOND);
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.acquire("umoja/jordan", "send", sixAMinute)).isZero();
        }
        assertThat(limiter.acquire("umoja/jordan", "send", sixAMinute)).isPositive();
    }

    @Test
    void theBucketsOfIdleCallersGo() {
        limiter.acquire("umoja/jordan", "send", limit(60, 10));
        limiter.acquire("umoja/amara", "send", limit(60, 10));
        now.addAndGet(5 * 60 * SECOND);
        limiter.acquire("umoja/amara", "send", limit(60, 10));
        now.addAndGet(6 * 60 * SECOND);
        limiter.evictIdle();
        assertThat(limiter.size()).isEqualTo(1);
    }
}
