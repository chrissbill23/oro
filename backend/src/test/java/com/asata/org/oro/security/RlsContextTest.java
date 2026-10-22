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
            assertThat(next.get("role")).isEqualTo("oro_service");
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
