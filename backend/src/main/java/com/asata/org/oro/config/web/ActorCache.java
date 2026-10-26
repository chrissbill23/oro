package com.asata.org.oro.config.web;

import com.asata.org.oro.enums.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What every request checks as oro_system (ActorResolver), kept a few seconds (oro.security.actor-cache-ttl, 5 s): the
 * identity of a token's account when it is active, and the current access of an identity to an organization. Two
 * system transactions a request otherwise (the load test: the system pool waits at 3,000 people).
 * <p>
 * Only what allows is kept: a new identity, a new access, a reactivation count at once. What takes away is forgotten
 * with its commit: a suspension, a deactivation (an erasure), a revocation (LiveAuthorization, with the streams), and
 * whatever the check of the streams finds every 30 s (a trigger, a script); anything else lasts at most the TTL. One
 * instance: the cache is its own (a second instance would need its evictions too, ORO-SCL-006).
 */
@Component
public class ActorCache {

    /** beyond it the cache starts again empty: a bound, never reached by the people of one instance */
    static final int MAX_ENTRIES = 100_000;

    private record IdentityKey(Product product, String externalUserId) {
    }

    private record AccessKey(UUID identityId, UUID organizationId) {
    }

    private record Entry<T>(T value, Instant expiresAt) {
    }

    private final Map<IdentityKey, Entry<UUID>> identities = new ConcurrentHashMap<>();
    private final Map<AccessKey, Instant> accesses = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final Clock clock;

    @Autowired
    public ActorCache(@Value("${oro.security.actor-cache-ttl:PT5S}") Duration ttl) {
        this(ttl, Clock.systemUTC());
    }

    ActorCache(Duration ttl, Clock clock) {
        this.ttl = ttl;
        this.clock = clock;
    }

    /** the active identity of the account, if known for less than the TTL; null: ask the database */
    public UUID identity(Product product, String externalUserId) {
        Entry<UUID> entry = identities.get(new IdentityKey(product, externalUserId));
        if (entry == null || !entry.expiresAt().isAfter(clock.instant())) {
            return null;
        }
        return entry.value();
    }

    /** the account's identity, read active */
    public void putIdentity(Product product, String externalUserId, UUID identityId) {
        if (ttl.isZero() || ttl.isNegative()) {
            return;
        }
        bound(identities);
        identities.put(new IdentityKey(product, externalUserId), new Entry<>(identityId, clock.instant().plus(ttl)));
    }

    /** the identity's access to the organization known current for less than the TTL; false: ask the database */
    public boolean granted(UUID identityId, UUID organizationId) {
        Instant expiresAt = accesses.get(new AccessKey(identityId, organizationId));
        return expiresAt != null && expiresAt.isAfter(clock.instant());
    }

    /** the access, read current */
    public void putGranted(UUID identityId, UUID organizationId) {
        if (ttl.isZero() || ttl.isNegative()) {
            return;
        }
        bound(accesses);
        accesses.put(new AccessKey(identityId, organizationId), clock.instant().plus(ttl));
    }

    /** the identity suspended, deactivated, erased: nothing of it kept, its accesses neither */
    public void forget(UUID identityId) {
        identities.values().removeIf(entry -> entry.value().equals(identityId));
        accesses.keySet().removeIf(key -> key.identityId().equals(identityId));
    }

    /** its access to the organization revoked */
    public void forget(UUID identityId, UUID organizationId) {
        accesses.remove(new AccessKey(identityId, organizationId));
    }

    /** everything forgotten: the tests, whose data is truncated under it */
    public void clear() {
        identities.clear();
        accesses.clear();
    }

    private static void bound(Map<?, ?> map) {
        if (map.size() >= MAX_ENTRIES) {
            map.clear();
        }
    }
}
