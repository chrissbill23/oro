package com.asata.org.oro.config.web;

import com.asata.org.oro.enums.Product;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** what a request checks, kept a few seconds; what takes it away, forgotten at once */
class ActorCacheTest {

    private Instant now = Instant.parse("2026-10-26T10:00:00Z");
    private final Clock clock = new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    };
    private final ActorCache cache = new ActorCache(Duration.ofSeconds(5), clock);
    private final UUID jordan = UUID.randomUUID();
    private final UUID acme = UUID.randomUUID();
    private final UUID globex = UUID.randomUUID();

    @Test
    void keptForItsTtlOnly() {
        assertThat(cache.identity(Product.UMOJA, "jordan")).isNull();
        cache.putIdentity(Product.UMOJA, "jordan", jordan);
        cache.putGranted(jordan, acme);
        assertThat(cache.identity(Product.UMOJA, "jordan")).isEqualTo(jordan);
        assertThat(cache.identity(Product.ORO, "jordan")).isNull();
        assertThat(cache.granted(jordan, acme)).isTrue();
        assertThat(cache.granted(jordan, globex)).isFalse();

        now = now.plusSeconds(5);
        assertThat(cache.identity(Product.UMOJA, "jordan")).isNull();
        assertThat(cache.granted(jordan, acme)).isFalse();
    }

    @Test
    void forgottenWhenTakenAway() {
        cache.putIdentity(Product.UMOJA, "jordan", jordan);
        cache.putGranted(jordan, acme);
        cache.putGranted(jordan, globex);

        cache.forget(jordan, acme);
        assertThat(cache.granted(jordan, acme)).isFalse();
        assertThat(cache.granted(jordan, globex)).isTrue();
        assertThat(cache.identity(Product.UMOJA, "jordan")).isEqualTo(jordan);

        cache.forget(jordan);
        assertThat(cache.identity(Product.UMOJA, "jordan")).isNull();
        assertThat(cache.granted(jordan, globex)).isFalse();
    }

    @Test
    void aTtlOfZeroKeepsNothing() {
        ActorCache off = new ActorCache(Duration.ZERO, clock);
        off.putIdentity(Product.UMOJA, "jordan", jordan);
        off.putGranted(jordan, acme);
        assertThat(off.identity(Product.UMOJA, "jordan")).isNull();
        assertThat(off.granted(jordan, acme)).isFalse();
    }

    @Test
    void boundedInSize() {
        for (int i = 0; i <= ActorCache.MAX_ENTRIES; i++) {
            cache.putGranted(UUID.randomUUID(), acme);
        }
        cache.putGranted(jordan, acme);
        assertThat(cache.granted(jordan, acme)).isTrue();
    }
}
