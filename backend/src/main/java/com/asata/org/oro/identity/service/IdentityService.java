package com.asata.org.oro.identity.service;

import com.asata.org.oro.context.DbSession;
import com.asata.org.oro.enums.IdentityStatus;
import com.asata.org.oro.enums.Product;
import com.asata.org.oro.exception.Forbidden;
import com.asata.org.oro.exception.NotFound;
import com.asata.org.oro.identity.repository.MessagingIdentityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.asata.org.oro.identity.entity.MessagingIdentity;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The messaging identities. Provisioning and status changes are for the trusted integration of each product (role
 * oro_system, behind a service-to-service authentication of the API, never a user token); resolving an account to its
 * identity is for the API (oro_app, its product only).
 */
@Service
@RequiredArgsConstructor
public class IdentityService {

    private final DbSession session;
    private final JdbcTemplate jdbc;
    private final MessagingIdentityRepository identities;

    /**
     * The identity of an account of a product, created at its first use; idempotent and safe under concurrency (the
     * unique (product_id, external_user_id): a second creation does nothing and reads the first).
     */
    @Transactional
    public UUID provision(Product product, String externalUserId) {
        session.enterSystem();
        jdbc.update("""
                INSERT INTO oro_messaging_identity(id, product_id, external_user_id) VALUES (?, ?, ?)
                ON CONFLICT (product_id, external_user_id) DO NOTHING""", UUID.randomUUID(), product.code(), externalUserId);
        return jdbc.queryForObject("SELECT id FROM oro_messaging_identity WHERE product_id = ? AND external_user_id = ?",
                UUID.class, product.code(), externalUserId);
    }

    /** the active identity of an authenticated account, for the API; NotFound when there is none */
    @Transactional(readOnly = true)
    public UUID resolve(Product product, String externalUserId) {
        session.enterProduct(product);
        return identities.findByProductAndExternalUserId(product, externalUserId)
                .filter(identity -> identity.getStatus() == IdentityStatus.ACTIVE)
                .orElseThrow(() -> new NotFound("No active messaging identity"))
                .getId();
    }

    /**
     * The active identity of an account whose token the API verified, created at its first request (the token of the
     * product proves the account exists). Forbidden when it is suspended or deactivated.
     */
    @Transactional
    public UUID resolveOrProvision(Product product, String externalUserId) {
        session.enterSystem();
        var rows = jdbc.queryForList("SELECT id, status FROM oro_messaging_identity WHERE product_id = ? AND external_user_id = ?",
                product.code(), externalUserId);
        if (rows.isEmpty()) {
            jdbc.update("""
                    INSERT INTO oro_messaging_identity(id, product_id, external_user_id) VALUES (?, ?, ?)
                    ON CONFLICT (product_id, external_user_id) DO NOTHING""", UUID.randomUUID(), product.code(), externalUserId);
            rows = jdbc.queryForList("SELECT id, status FROM oro_messaging_identity WHERE product_id = ? AND external_user_id = ?",
                    product.code(), externalUserId);
        }
        var row = rows.getFirst();
        if (!IdentityStatus.ACTIVE.code().equals(row.get("status"))) {
            throw new Forbidden("The messaging account is not active");
        }
        return (UUID) row.get("id");
    }

    /** oro_system: the identity of an account, whatever its status */
    @Transactional(readOnly = true)
    public Optional<UUID> find(Product product, String externalUserId) {
        session.enterSystem();
        return jdbc.queryForList("SELECT id FROM oro_messaging_identity WHERE product_id = ? AND external_user_id = ?",
                UUID.class, product.code(), externalUserId).stream().findFirst();
    }

    /** the external user ids of identities of the product (the ids the clients know), for the API responses */
    @Transactional(readOnly = true)
    public Map<UUID, String> externalIds(Product product, Collection<UUID> identityIds) {
        session.enterProduct(product);
        return identities.findAllById(identityIds).stream()
                .collect(Collectors.toMap(MessagingIdentity::getId, MessagingIdentity::getExternalUserId));
    }

    /** suspended: keeps its conversations, reads and sends nothing until it is active again */
    @Transactional
    public void suspend(UUID identityId) {
        setStatus(identityId, "suspended", "status = 'active'");
    }

    @Transactional
    public void reactivate(UUID identityId) {
        setStatus(identityId, "active", "status = 'suspended'");
    }

    /** final: the identity leaves all its conversations and loses its organization accesses (trigger) */
    @Transactional
    public void deactivate(UUID identityId) {
        session.enterSystem();
        int updated = jdbc.update("""
                UPDATE oro_messaging_identity SET status = 'deactivated', deactivated_at = now()
                WHERE id = ? AND status <> 'deactivated'""", identityId);
        if (updated == 0 && !exists(identityId)) {
            throw new NotFound("Messaging identity not found");
        }
    }

    private void setStatus(UUID identityId, String status, String from) {
        session.enterSystem();
        // status and from: constants of this class
        int updated = jdbc.update("UPDATE oro_messaging_identity SET status = '" + status + "' WHERE id = ? AND " + from,
                identityId);
        if (updated == 0 && !exists(identityId)) {
            throw new NotFound("Messaging identity not found");
        }
    }

    private boolean exists(UUID identityId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM oro_messaging_identity WHERE id = ?)", Boolean.class, identityId));
    }
}
