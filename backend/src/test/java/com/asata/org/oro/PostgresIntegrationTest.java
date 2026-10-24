package com.asata.org.oro;

import com.asata.org.oro.attachment.service.AttachmentService;
import com.asata.org.oro.context.Actor;
import com.asata.org.oro.context.DbSession;
import com.asata.org.oro.conversation.service.ConversationService;
import com.asata.org.oro.enums.Product;
import com.asata.org.oro.identity.service.IdentityService;
import com.asata.org.oro.identity.service.OrganizationAccessService;
import com.asata.org.oro.message.dto.SendMessage;
import com.asata.org.oro.message.dto.SendResult;
import com.asata.org.oro.message.service.MessageService;
import com.asata.org.oro.outbox.service.OutboxWorker;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * The tests run on a PostgreSQL 17 (Testcontainers, Docker required) created by the real scripts of
 * 1_ASATA_DATABASE (CREATE_ORO_TABLES.sql: tables, triggers, roles, RLS), as the backend runs: the API's login oro_api
 * (NOINHERIT) taking oro_app per transaction, the worker's login oro_worker taking oro_system. {@link #admin} is the superuser of the container (it
 * bypasses RLS): only to prepare and check the rows. Every test starts on empty tables.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestOutboxHandler.class)
public abstract class PostgresIntegrationTest {

    /** the organizations of Umoja the tests use: external references, nothing to create */
    protected static final UUID ACME = UUID.fromString("22222222-0000-4000-8000-0000000000a1");
    protected static final UUID GLOBEX = UUID.fromString("22222222-0000-4000-8000-0000000000b1");

    protected static final Path DATABASE_DIR =
            Path.of(System.getProperty("asata.database.dir", "../../1_ASATA_DATABASE")).toAbsolutePath().normalize();

    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17")
            // each context the tests keep has its three pools open (the API's, the worker's, the accounts')
            .withCommand("postgres", "-c", "max_connections=300")
            // the accounts of Asata (asata_user...), which Oro's accounts sign in
            .withCopyFileToContainer(MountableFile.forHostPath(DATABASE_DIR.resolve("CREATE_ASATA_TABLES.sql")),
                    "/docker-entrypoint-initdb.d/00-asata-create.sql")
            .withCopyFileToContainer(MountableFile.forHostPath(DATABASE_DIR.resolve("CREATE_ASATA_TABLES.sql")),
                    "/scripts/CREATE_ASATA_TABLES.sql")
            .withCopyFileToContainer(MountableFile.forHostPath(DATABASE_DIR.resolve("CREATE_ORO_TABLES.sql")),
                    "/docker-entrypoint-initdb.d/01-oro-create.sql")
            .withCopyFileToContainer(MountableFile.forClasspathResource("test-login.sql"),
                    "/docker-entrypoint-initdb.d/02-test-login.sql")
            // to run them again in a test
            .withCopyFileToContainer(MountableFile.forHostPath(DATABASE_DIR.resolve("CREATE_ORO_TABLES.sql")),
                    "/scripts/CREATE_ORO_TABLES.sql")
            .withCopyFileToContainer(MountableFile.forHostPath(DATABASE_DIR.resolve("oro/INSERT.sql")),
                    "/scripts/oro/INSERT.sql")
            .withCopyFileToContainer(MountableFile.forHostPath(DATABASE_DIR.resolve("oro/migrate.sh")),
                    "/scripts/oro/migrate.sh")
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("ops/outbox-replay.sh").toAbsolutePath()),
                    "/scripts/ops/outbox-replay.sh");

    protected static final JdbcTemplate admin;

    static {
        POSTGRES.start();
        admin = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword()));
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "oro_api");
        registry.add("spring.datasource.password", () -> "oro_api_test");
        registry.add("oro.system-datasource.url", POSTGRES::getJdbcUrl);
        registry.add("oro.system-datasource.username", () -> "oro_worker");
        registry.add("oro.system-datasource.password", () -> "oro_worker_test");
        registry.add("oro.account-datasource.url", POSTGRES::getJdbcUrl);
        registry.add("oro.account-datasource.username", () -> "oro_auth");
        registry.add("oro.account-datasource.password", () -> "oro_auth_test");
        // Oro's accounts sign with the key of the test tokens of ORO: the tokens of the tests and the ones its sign-in
        // gives are checked the same way, with its own public keys. Umoja's, as Umoja publishes them (new for each run)
        registry.add("oro.accounts.signing-key", () -> TestTokens.privateKeyPkcs8(TestTokens.ORO));
        registry.add("oro.accounts.cookie-secure", () -> "false");
        registry.add("oro.security.products.UMOJA.jwk-set", () -> TestTokens.publicJwkSet(TestTokens.UMOJA));
    }

    @Autowired
    protected IdentityService identities;
    @Autowired
    protected OrganizationAccessService organizationAccess;
    @Autowired
    protected ConversationService conversations;
    @Autowired
    protected MessageService messages;
    @Autowired
    protected AttachmentService attachments;
    @Autowired
    protected OutboxWorker outboxWorker;
    @Autowired
    protected TestOutboxHandler outboxHandler;
    @Autowired
    protected com.asata.org.oro.config.ratelimit.RateLimiter rateLimiter;
    @Autowired
    protected DbSession session;
    /** the JdbcTemplate of the API: oro_api, in the transactions of {@link #tx} */
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    protected TransactionTemplate tx;

    @BeforeEach
    void emptyTables() {
        tx = new TransactionTemplate(transactionManager);
        admin.execute("""
                TRUNCATE oro_outbox_event, oro_message_reaction, oro_attachment, oro_message, oro_conversation_member, oro_conversation,
                         oro_organization_access, oro_organization_sync, oro_messaging_identity, asata_user CASCADE""");
        outboxHandler.reset();
        // the limits of the users count again: a test is not limited by the requests of the others
        rateLimiter.reset();
    }

    protected UUID oroUser(String name) {
        return identities.provision(Product.ORO, "oro-" + name);
    }

    /** a Umoja identity with the access to the organizations */
    protected UUID umojaUser(String name, UUID... organizationIds) {
        UUID id = identities.provision(Product.UMOJA, "umoja-" + name);
        for (UUID organizationId : organizationIds) {
            organizationAccess.grant(id, organizationId);
        }
        return id;
    }

    protected SendResult say(Actor actor, UUID conversationId, String body) {
        return messages.send(actor, conversationId, SendMessage.text("key-" + UUID.randomUUID(), body));
    }

    /** raw SQL as oro_app for the actor, past the checks of the services: what RLS and the triggers alone allow */
    protected <T> T asUser(Actor actor, Function<JdbcTemplate, T> work) {
        return tx.execute(status -> {
            session.enterUser(actor);
            return work.apply(jdbc);
        });
    }

    /** a count as oro_app for the actor: what RLS shows it */
    protected long rawCount(Actor actor, String sql, Object... args) {
        Long count = asUser(actor, jdbc -> jdbc.queryForObject(sql, Long.class, args));
        return count == null ? 0 : count;
    }

    protected long count(String sql, Object... args) {
        Long count = admin.queryForObject(sql, Long.class, args);
        return count == null ? 0 : count;
    }

    /** the call fails, and the message of one of its causes contains the fragment */
    protected static void assertRefused(ThrowingCallable call, String fragment) {
        Throwable thrown = catchThrowable(call);
        assertThat(thrown).as("expected a failure containing \"%s\"", fragment).isNotNull();
        StringBuilder messages = new StringBuilder();
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            messages.append(t.getClass().getName()).append(": ").append(t.getMessage()).append('\n');
        }
        assertThat(messages.toString()).contains(fragment);
    }

    /** runs the task n times at once (released together); the results in order */
    protected static <T> List<T> concurrently(int n, IntFunction<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.apply(index);
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
