package com.asata.org.oro.context;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.orm.jpa.JpaTransactionManager;

import javax.sql.DataSource;

/**
 * Three logins, three pools. The API's (spring.datasource, a member of oro_app only) serves the requests of the users,
 * JPA included: it can never take oro_system, whatever a request makes it run. The worker's (oro.system-datasource, a
 * member of oro_system only) serves the trusted integrations and the workers, in transactions of
 * {@link DbSession#SYSTEM_TRANSACTION_MANAGER}. The accounts' (oro.account-datasource, a member of oro_account only)
 * serves the sign-in: the only one that reads a password, and it reads nothing of the messaging. All NOINHERIT: no right
 * without a context.
 */
@Configuration
public class DataSourceConfig {

    /** the driver keeps the details of a refused row (its values) out of its messages, so out of the logs */
    private static final String NO_ERROR_DETAIL = "logServerErrorDetail";

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    HikariDataSource dataSource(DataSourceProperties dataSourceProperties,
                                @Value("${oro.database.api-pool-size:0}") int size) {
        HikariDataSource dataSource = dataSourceProperties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
        dataSource.setPoolName("oro-api");
        dataSource.setMaximumPoolSize(PoolSizes.api(size, Runtime.getRuntime().availableProcessors()));
        dataSource.addDataSourceProperty(NO_ERROR_DETAIL, "false");
        return dataSource;
    }

    @Bean
    @ConfigurationProperties("oro.system-datasource")
    DataSourceProperties systemDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("oro.system-datasource.hikari")
    HikariDataSource systemDataSource(@Qualifier("systemDataSourceProperties") DataSourceProperties properties,
                                      @Value("${oro.database.system-pool-size:0}") int size) {
        HikariDataSource dataSource = properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
        dataSource.setPoolName("oro-system");
        dataSource.setMaximumPoolSize(PoolSizes.system(size, Runtime.getRuntime().availableProcessors()));
        dataSource.addDataSourceProperty(NO_ERROR_DETAIL, "false");
        return dataSource;
    }

    @Bean
    @Primary
    JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    JdbcTemplate systemJdbcTemplate(@Qualifier("systemDataSource") DataSource systemDataSource) {
        return new JdbcTemplate(systemDataSource);
    }

    /** the transactions of the API: JPA, on the API's pool (the default of @Transactional) */
    @Bean
    @Primary
    JpaTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    @Bean
    @ConfigurationProperties("oro.account-datasource")
    DataSourceProperties accountDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("oro.account-datasource.hikari")
    HikariDataSource accountDataSource(@Qualifier("accountDataSourceProperties") DataSourceProperties properties) {
        HikariDataSource dataSource = properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
        dataSource.setPoolName("oro-account");
        dataSource.addDataSourceProperty(NO_ERROR_DETAIL, "false");
        return dataSource;
    }

    @Bean
    JdbcTemplate accountJdbcTemplate(@Qualifier("accountDataSource") DataSource accountDataSource) {
        return new JdbcTemplate(accountDataSource);
    }

    /** the transactions of the accounts: JDBC, on the accounts' pool */
    @Bean(com.asata.org.oro.account.AccountDb.TRANSACTION_MANAGER)
    JdbcTransactionManager accountTransactionManager(@Qualifier("accountDataSource") DataSource accountDataSource) {
        return new JdbcTransactionManager(accountDataSource);
    }

    /** the transactions of the integrations and the workers: JDBC, on the worker's pool */
    @Bean(DbSession.SYSTEM_TRANSACTION_MANAGER)
    JdbcTransactionManager systemTransactionManager(@Qualifier("systemDataSource") DataSource systemDataSource) {
        return new JdbcTransactionManager(systemDataSource);
    }
}
