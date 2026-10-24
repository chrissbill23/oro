package com.asata.org.oro.account;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Function;

/**
 * The transactions of the accounts: the accounts' login (oro_auth) takes oro_account for the transaction only, with
 * short timeouts. Nothing else of Oro uses this pool, and oro_account reads nothing of the messaging.
 */
@Component
public class AccountDb {

    public static final String TRANSACTION_MANAGER = "accountTransactionManager";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public AccountDb(@Qualifier("accountJdbcTemplate") JdbcTemplate jdbc,
                     @Qualifier(TRANSACTION_MANAGER) PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** work in one transaction as oro_account; its result */
    public <T> T inTransaction(Function<JdbcTemplate, T> work) {
        return tx.execute(status -> {
            jdbc.execute("SET LOCAL ROLE oro_account");
            jdbc.queryForObject("SELECT set_config('statement_timeout', '5s', true), set_config('lock_timeout', '2s', true)",
                    (rs, i) -> 0);
            return work.apply(jdbc);
        });
    }
}
