package com.asata.org.oro.identity.service;

import com.asata.org.oro.context.DbSession;
import com.asata.org.oro.enums.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The erasure of a person's data in Oro (oro_erasure, oro_erase_identity): asked by the person (self) or by its
 * product's integration, the identity deactivated at once (its conversations left, its streams closed), then erased a
 * batch at a time by the worker (ErasureScheduler): its messages become deletions, its reactions and uploads go, the
 * messages of others that name it name an anonymous id, and so does the identity at the end. Or in one organization
 * only (oro_organization_erasure, oro_erase_identity_in_organization; Umoja, asked by the organization): the same in
 * its conversations, the identity and its other organizations untouched.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErasureService {

    static final int BATCH = 1000;

    private final DbSession session;
    private final IdentityService identities;

    /** the erasure of the identity of this account asked; false when Oro has nothing of it (never seen, or erased) */
    @Transactional(transactionManager = DbSession.SYSTEM_TRANSACTION_MANAGER)
    public boolean request(Product product, String externalUserId, String requestedBy) {
        Optional<UUID> identity = identities.find(product, externalUserId);
        if (identity.isEmpty()) {
            return false;
        }
        session.enterSystem();
        session.jdbc().update("INSERT INTO oro_erasure(identity_id, product_id, requested_by) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                identity.get(), product.code(), requestedBy);
        identities.deactivate(identity.get());
        log.info("erasure of identity {} asked ({})", identity.get(), requestedBy);
        return true;
    }

    /**
     * the erasure of what this person has in one organization asked (Umoja); false when Oro has nothing of it. Asked
     * twice before it is done: once
     */
    @Transactional(transactionManager = DbSession.SYSTEM_TRANSACTION_MANAGER)
    public boolean requestInOrganization(Product product, String externalUserId, UUID organizationId) {
        Optional<UUID> identity = identities.find(product, externalUserId);
        if (identity.isEmpty()) {
            return false;
        }
        session.enterSystem();
        session.jdbc().update("""
                INSERT INTO oro_organization_erasure(identity_id, product_id, organization_id) VALUES (?, ?, ?)
                ON CONFLICT (identity_id, organization_id) WHERE completed_at IS NULL DO NOTHING""",
                identity.get(), product.code(), organizationId);
        log.info("erasure of identity {} in organization {} asked", identity.get(), organizationId);
        return true;
    }

    /** the erasure of one identity carried out, batch after batch (each its own transaction); the rows changed */
    public long erase(UUID identityId) {
        long total = 0;
        for (int step; (step = step(identityId)) > 0; ) {
            total += step;
        }
        log.info("identity {} erased: {} rows changed", identityId, total);
        return total;
    }

    /** the erasures asked and not done, the oldest first; the number done */
    public int erasePending() {
        List<UUID> pending = session.systemTransaction().execute(status -> {
            session.enterSystem();
            return session.jdbc().queryForList(
                    "SELECT identity_id FROM oro_erasure WHERE completed_at IS NULL ORDER BY requested_at LIMIT 20", UUID.class);
        });
        pending.forEach(this::erase);
        List<UUID> inOrganizations = session.systemTransaction().execute(status -> {
            session.enterSystem();
            return session.jdbc().queryForList(
                    "SELECT id FROM oro_organization_erasure WHERE completed_at IS NULL ORDER BY requested_at LIMIT 20", UUID.class);
        });
        inOrganizations.forEach(this::eraseInOrganization);
        return pending.size() + inOrganizations.size();
    }

    /** one erasure in an organization carried out, batch after batch; the rows changed */
    public long eraseInOrganization(UUID erasureId) {
        long total = 0;
        while (true) {
            Integer changed = session.systemTransaction().execute(status -> {
                session.enterSystem();
                return session.jdbc().queryForObject("SELECT oro_erase_identity_in_organization(?, ?)", Integer.class,
                        erasureId, BATCH);
            });
            if (changed == null || changed == 0) {
                break;
            }
            total += changed;
        }
        log.info("erasure {} in an organization done: {} rows changed", erasureId, total);
        return total;
    }

    private int step(UUID identityId) {
        Integer changed = session.systemTransaction().execute(status -> {
            session.enterSystem();
            return session.jdbc().queryForObject("SELECT oro_erase_identity(?, ?)", Integer.class, identityId, BATCH);
        });
        return changed == null ? 0 : changed;
    }
}
