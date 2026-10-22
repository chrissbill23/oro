package com.asata.org.oro.identity.service;

import com.asata.org.oro.context.DbSession;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The organization accesses of the Umoja identities: Oro's copy of who may take part in the conversations of a Umoja
 * organization. Umoja owns the memberships; its trusted integration (role oro_system) pushes each change here (grant,
 * revoke) and reconciles periodically. Oro never reads the database of Umoja. Revoking ends at once, in the same
 * transaction, the memberships of the identity in the conversations of the organization (trigger).
 */
@Service
@RequiredArgsConstructor
public class OrganizationAccessService {

    private final DbSession session;
    private final JdbcTemplate jdbc;

    /** idempotent; a revoked access is granted again. Only a Umoja identity (composite foreign key on the product). */
    @Transactional
    public void grant(UUID identityId, UUID organizationId) {
        session.enterSystem();
        jdbc.update("""
                INSERT INTO oro_organization_access(identity_id, organization_id) VALUES (?, ?)
                ON CONFLICT (identity_id, organization_id) DO UPDATE SET revoked_at = NULL, granted_at = now()
                WHERE oro_organization_access.revoked_at IS NOT NULL""", identityId, organizationId);
    }

    /** the identity has a current access to the organization */
    @Transactional(readOnly = true)
    public boolean isGranted(UUID identityId, UUID organizationId) {
        session.enterSystem();
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM oro_organization_access
                               WHERE identity_id = ? AND organization_id = ? AND revoked_at IS NULL)""",
                Boolean.class, identityId, organizationId));
    }

    /** idempotent */
    @Transactional
    public void revoke(UUID identityId, UUID organizationId) {
        session.enterSystem();
        jdbc.update("""
                UPDATE oro_organization_access SET revoked_at = now()
                WHERE identity_id = ? AND organization_id = ? AND revoked_at IS NULL""", identityId, organizationId);
    }
}
