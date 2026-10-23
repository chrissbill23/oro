package com.asata.org.oro.security;

import com.asata.org.oro.PostgresIntegrationTest;
import com.asata.org.oro.context.Actor;
import com.asata.org.oro.enums.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The context of RLS lives in the transaction only: a pooled connection gives the next transaction nothing of the
 * previous one, and a transaction without context has no right at all (the login role is NOINHERIT).
 */
class RlsContextTest extends PostgresIntegrationTest {

    private Actor amaniActor;
    private UUID jordan;

    @BeforeEach
    void data() {
        UUID amani = oroUser("amani");
        UUID baraka = oroUser("baraka");
        amaniActor = Actor.oro(amani);
        conversations.openDirect(amaniActor, baraka);
        jordan = umojaUser("jordan", ACME);
        conversations.createGroup(Actor.umoja(jordan, ACME), "Design", null, List.of());
    }

    private Map<String, Object> connectionState() {
        return tx.execute(status -> jdbc.queryForMap("""
                SELECT current_user AS role, current_setting('oro.product_id', true) AS product,
                       current_setting('oro.identity_id', true) AS identity, pg_backend_pid() AS pid"""));
    }

    /** whatever runs on the API's connections (an injection included) cannot become oro_system */
    @Test
    void theApiLoginNeverTakesTheSystemRole() {
        assertRefused(() -> tx.execute(status -> jdbc.queryForObject(
                "SELECT set_config('role', 'oro_system', true)", String.class)), "permission denied");
        assertRefused(() -> tx.execute(status -> {
            jdbc.execute("SET LOCAL ROLE oro_system");
            return null;
        }), "permission denied");
        assertRefused(() -> session.systemTransaction().execute(status -> {
            session.jdbc().execute("SET LOCAL ROLE oro_app");
            return null;
        }), "permission denied");
        // each context on its own login only
        assertRefused(() -> tx.execute(status -> {
            session.enterSystem();
            return null;
        }), "the database context of the system is set inside its own transaction only");
        assertRefused(() -> session.systemTransaction().execute(status -> {
            session.enterUser(amaniActor);
            return null;
        }), "the database context of a user is set inside its own transaction only");
        String logins = session.systemTransaction().execute(status -> {
            session.enterSystem();
            return session.jdbc().queryForObject("SELECT session_user || '/' || current_user", String.class);
        });
        assertThat(logins).isEqualTo("oro_worker/oro_system");
    }

    /** a user's transaction is bounded by the server: a slow statement and a long wait for a lock end (test: 2 s, 1 s) */
    @Test
    void aUserTransactionIsBoundedInTime() {
        assertRefused(() -> asUser(amaniActor, jdbc -> jdbc.queryForObject("SELECT pg_sleep(5)::text", String.class)),
                "statement timeout");
        UUID conversationId = admin.queryForObject("SELECT id FROM oro_conversation WHERE product_id = 'ORO'", UUID.class);
        // the superuser holds the conversation's row; the user's send-like lock waits, then gives up
        java.util.concurrent.CompletableFuture<Void> holder = java.util.concurrent.CompletableFuture.runAsync(() ->
                admin.execute("BEGIN; SELECT 1 FROM oro_conversation WHERE id = '%s' FOR UPDATE; SELECT pg_sleep(3); COMMIT;"
                        .formatted(conversationId)));
        try {
            Thread.sleep(300);
            assertRefused(() -> asUser(amaniActor, jdbc -> jdbc.queryForObject(
                    "SELECT id FROM oro_conversation WHERE id = ? FOR NO KEY UPDATE", UUID.class, conversationId)),
                    "lock timeout");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            holder.join();
        }
        String settings = asUser(amaniActor, jdbc -> jdbc.queryForObject(
                "SELECT current_setting('statement_timeout') || ' ' || current_setting('idle_in_transaction_session_timeout')", String.class));
        assertThat(settings).isEqualTo("2s 30s");
    }

    @Test
    void aTransactionWithoutContextHasNoRight() {
        assertRefused(() -> tx.execute(status -> jdbc.queryForObject("SELECT count(*) FROM oro_conversation", Long.class)),
                "permission denied");
        assertRefused(() -> tx.execute(status -> jdbc.queryForObject("SELECT count(*) FROM oro_message", Long.class)),
                "permission denied");
    }

    @Test
    void theContextDoesNotLeakToTheNextTransactionOfAPooledConnection() {
        Set<Integer> userPids = new HashSet<>();
        Set<Integer> nextPids = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            Integer pid = tx.execute(status -> {
                session.enterUser(amaniActor);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM oro_conversation", Long.class)).isEqualTo(1);
                return jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
            });
            userPids.add(pid);
            if (i % 2 == 0) {
                // a rolled back transaction too
                tx.executeWithoutResult(status -> {
                    session.enterUser(amaniActor);
                    status.setRollbackOnly();
                });
            }
            Map<String, Object> next = connectionState();
            nextPids.add((Integer) next.get("pid"));
            assertThat(next.get("role")).isEqualTo("oro_api");
            assertThat((String) next.get("product")).isNullOrEmpty();
            assertThat((String) next.get("identity")).isNullOrEmpty();
        }
        // the same connections were reused: the check above saw them after a user transaction
        userPids.retainAll(nextPids);
        assertThat(userPids).isNotEmpty();
    }

    @Test
    void aProductWithoutIdentitySeesItsIdentitiesButNoConversation() {
        Long oroIdentities = tx.execute(status -> {
            session.enterProduct(Product.ORO);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM oro_conversation", Long.class)).isZero();
            return jdbc.queryForObject("SELECT count(*) FROM oro_messaging_identity", Long.class);
        });
        assertThat(oroIdentities).isEqualTo(2);
        assertThat(rawCount(Actor.umoja(jordan, ACME), "SELECT count(*) FROM oro_messaging_identity WHERE product_id = 'ORO'")).isZero();
        assertThat(rawCount(Actor.umoja(jordan, ACME), "SELECT count(*) FROM oro_conversation")).isEqualTo(1);
    }

    @Test
    void theContextIsOnlySetInsideATransaction() {
        assertThatThrownBy(() -> session.enterUser(amaniActor)).isInstanceOf(IllegalStateException.class);
    }
}
