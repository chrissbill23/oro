package com.asata.org.oro.context;

import com.asata.org.oro.enums.Product;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;

/**
 * The database context of the current transaction, its first statements. Each login role is NOINHERIT: it has no
 * right by itself, so a transaction that forgets to enter a context fails (permission denied) instead of seeing too
 * much. Everything is transaction-local (SET LOCAL ROLE, set_config(..., true)): reset at the commit or the rollback,
 * so nothing leaks to the next user of the pooled connection.
 * <ul>
 * <li>{@link #enterUser}: role oro_app, RLS shows the product and the rows of the identity only</li>
 * <li>{@link #enterProduct}: role oro_app, the product only (to resolve an external user id to its identity)</li>
 * <li>{@link #enterSystem}: role oro_system, the trusted integrations and workers, in a transaction of
 * {@link #SYSTEM_TRANSACTION_MANAGER} only: on the worker's login, never on the API's (see DataSourceConfig)</li>
 * </ul>
 * Each context also bounds its transaction (oro.database.*): a statement, a wait for a lock, and a transaction left idle
 * are ended by the server, whatever runs them (JPA, JDBC).
 */
@Component
public class DbSession {

    /** the transaction manager of the system work: {@code @Transactional(transactionManager = SYSTEM_TRANSACTION_MANAGER)} */
    public static final String SYSTEM_TRANSACTION_MANAGER = "systemTransactionManager";

    private final JdbcTemplate jdbc;
    private final JdbcTemplate systemJdbc;
    private final DataSource dataSource;
    private final DataSource systemDataSource;
    private final PlatformTransactionManager systemTransactionManager;
    private final Timeouts userTimeouts;
    private final Timeouts systemTimeouts;

    /** PostgreSQL durations ('10s', '500ms'); 0 disables one */
    record Timeouts(String statement, String lock, String idleInTransaction) {
    }

    public DbSession(JdbcTemplate jdbc, @Qualifier("systemJdbcTemplate") JdbcTemplate systemJdbc,
                     DataSource dataSource, @Qualifier("systemDataSource") DataSource systemDataSource,
                     @Qualifier(SYSTEM_TRANSACTION_MANAGER) PlatformTransactionManager systemTransactionManager,
                     @Value("${oro.database.user.statement-timeout:10s}") String userStatement,
                     @Value("${oro.database.user.lock-timeout:5s}") String userLock,
                     @Value("${oro.database.user.idle-in-transaction-timeout:30s}") String userIdle,
                     @Value("${oro.database.system.statement-timeout:60s}") String systemStatement,
                     @Value("${oro.database.system.lock-timeout:10s}") String systemLock,
                     @Value("${oro.database.system.idle-in-transaction-timeout:60s}") String systemIdle) {
        this.jdbc = jdbc;
        this.systemJdbc = systemJdbc;
        this.dataSource = dataSource;
        this.systemDataSource = systemDataSource;
        this.systemTransactionManager = systemTransactionManager;
        this.userTimeouts = new Timeouts(userStatement, userLock, userIdle);
        this.systemTimeouts = new Timeouts(systemStatement, systemLock, systemIdle);
    }

    /** a new system transaction, for the work that is not a @Transactional method */
    public TransactionTemplate systemTransaction() {
        return new TransactionTemplate(systemTransactionManager);
    }

    public void enterUser(Actor actor) {
        requireTransaction(dataSource, "a user");
        enter(jdbc, "oro_app", actor.product(), actor.identityId().toString(), userTimeouts);
    }

    public void enterProduct(Product product) {
        requireTransaction(dataSource, "a user");
        enter(jdbc, "oro_app", product, "", userTimeouts);
    }

    public void enterSystem() {
        requireTransaction(systemDataSource, "the system");
        enter(systemJdbc, "oro_system", null, "", systemTimeouts);
    }

    /** the JdbcTemplate of the current transaction: the worker's in a system transaction, else the API's */
    public JdbcTemplate jdbc() {
        return inSystemTransaction() ? systemJdbc : jdbc;
    }

    public boolean inSystemTransaction() {
        return TransactionSynchronizationManager.getResource(systemDataSource) != null;
    }

    private static void requireTransaction(DataSource dataSource, String whose) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.getResource(dataSource) == null) {
            throw new IllegalStateException("the database context of " + whose + " is set inside its own transaction only");
        }
    }

    private static void enter(JdbcTemplate jdbc, String role, Product product, String identityId, Timeouts timeouts) {
        // role: one of two constants, never an input
        jdbc.execute("SET LOCAL ROLE " + role);
        jdbc.queryForList("""
                SELECT set_config('oro.product_id', ?, true), set_config('oro.identity_id', ?, true),
                       set_config('statement_timeout', ?, true), set_config('lock_timeout', ?, true),
                       set_config('idle_in_transaction_session_timeout', ?, true)""",
                product == null ? "" : product.code(), identityId,
                timeouts.statement(), timeouts.lock(), timeouts.idleInTransaction());
    }
}
