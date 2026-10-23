package com.asata.org.oro.live;

import com.asata.org.oro.context.DbSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Array;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps the open streams (LiveHub) as authorized as a new request would be. A stream is closed:
 * <ul>
 * <li>when its token expires (the timeout of the stream, and {@link #check} as a backstop);</li>
 * <li>right after the commit that suspends or deactivates its identity, or revokes its organization access;</li>
 * <li>by {@link #check}, every 30 s, when its identity is no longer active or its access no longer current, whatever
 * changed them (a trigger, another instance, a script).</li>
 * </ul>
 * Its user is then offline for the others: a revoked user neither hears nor shows in the presence of its former
 * organization.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveAuthorization {

    private final LiveHub hub;
    private final DbSession session;

    /** after the commit of the current transaction (at once without one): the identity's streams end */
    public void closeAfterCommit(UUID identityId) {
        afterCommit(() -> hub.close(identityId));
    }

    /** after the commit of the current transaction (at once without one): the identity's streams in the organization end */
    public void closeAfterCommit(UUID identityId, UUID organizationId) {
        afterCommit(() -> hub.close(identityId, organizationId));
    }

    /** closes the expired streams and those whose identity or organization access is no longer valid; the number closed */
    @Scheduled(fixedDelayString = "${oro.live.authorization-check:PT30S}")
    public int check() {
        int closed = hub.closeExpired();
        Set<LiveHub.Subject> subjects = hub.subjects();
        if (subjects.isEmpty()) {
            return closed;
        }
        UUID[] identityIds = subjects.stream().map(LiveHub.Subject::identityId).distinct().toArray(UUID[]::new);
        Allowed allowed = session.systemTransaction().execute(status -> {
            session.enterSystem();
            List<UUID> active = session.jdbc().queryForList(
                    "SELECT id FROM oro_messaging_identity WHERE id = ANY(?) AND status = 'active'", UUID.class,
                    (Object) uuidArray(identityIds));
            Set<String> accesses = new HashSet<>(session.jdbc().queryForList("""
                    SELECT identity_id || '/' || organization_id FROM oro_organization_access
                    WHERE identity_id = ANY(?) AND revoked_at IS NULL""", String.class, (Object) uuidArray(identityIds)));
            return new Allowed(new HashSet<>(active), accesses);
        });
        Objects.requireNonNull(allowed);
        for (LiveHub.Subject subject : subjects) {
            if (!allowed.active().contains(subject.identityId())) {
                closed += hub.close(subject.identityId());
            } else if (subject.organizationId() != null
                    && !allowed.accesses().contains(subject.identityId() + "/" + subject.organizationId())) {
                closed += hub.close(subject.identityId(), subject.organizationId());
            }
        }
        if (closed > 0) {
            log.info("{} stream(s) closed: token expired, identity inactive or organization access revoked", closed);
        }
        return closed;
    }

    private record Allowed(Set<UUID> active, Set<String> accesses) {
    }

    private Array uuidArray(UUID[] ids) {
        return session.jdbc().execute((java.sql.Connection connection) -> connection.createArrayOf("uuid", ids));
    }

    private static void afterCommit(Runnable close) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            close.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                close.run();
            }
        });
    }
}
