package com.asata.org.oro.context;

import com.asata.org.oro.enums.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The database context of the current transaction, its first statements. The login role (oro_service) is NOINHERIT:
 * it has no right by itself, so a transaction that forgets to enter a context fails (permission denied) instead of
 * seeing too much. Everything is transaction-local (SET LOCAL ROLE, set_config(..., true)): reset at the commit or
 * the rollback, so nothing leaks to the next user of the pooled connection.
 * <ul>
 * <li>{@link #enterUser}: role oro_app, RLS shows the product and the rows of the identity only</li>
 * <li>{@link #enterProduct}: role oro_app, the product only (to resolve an external user id to its identity)</li>
 * <li>{@link #enterSystem}: role oro_system, the trusted integrations and workers</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class DbSession {

    private final JdbcTemplate jdbc;

    public void enterUser(Actor actor) {
        enter("oro_app", actor.product(), actor.identityId().toString());
    }

    public void enterProduct(Product product) {
        enter("oro_app", product, "");
    }

    public void enterSystem() {
        enter("oro_system", null, "");
    }

    private void enter(String role, Product product, String identityId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("the database context is set inside a transaction only");
        }
        // role: one of two constants, never an input
        jdbc.execute("SET LOCAL ROLE " + role);
        jdbc.queryForList("SELECT set_config('oro.product_id', ?, true), set_config('oro.identity_id', ?, true)",
                product == null ? "" : product.code(), identityId);
    }
}
