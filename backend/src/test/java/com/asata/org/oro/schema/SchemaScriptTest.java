package com.asata.org.oro.schema;

import com.asata.org.oro.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container.ExecResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** the scripts of 1_ASATA_DATABASE: the schema (run at the creation of the container) and the development seed */
class SchemaScriptTest extends PostgresIntegrationTest {

    private ExecResult psql(String file) throws Exception {
        return POSTGRES.execInContainer("psql", "-q", "-v", "ON_ERROR_STOP=1", "-U", POSTGRES.getUsername(),
                "-d", POSTGRES.getDatabaseName(), "-f", file);
    }

    @Test
    void theSchemaScriptRunsAgainOnAnExistingDatabase() throws Exception {
        ExecResult result = psql("/scripts/CREATE_ORO_TABLES.sql");
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
    }

    @Test
    void everyTableHasRowLevelSecurityAndBelongsToOroOwner() {
        List<Map<String, Object>> tables = admin.queryForList("""
                SELECT relname, relrowsecurity, pg_get_userbyid(relowner) AS owner FROM pg_class
                WHERE relname LIKE 'oro\\_%' AND relkind = 'r' ORDER BY relname""");
        assertThat(tables).extracting(t -> t.get("relname")).containsExactly("oro_attachment", "oro_conversation",
                "oro_conversation_member", "oro_erasure", "oro_identity_block", "oro_message", "oro_message_reaction", "oro_messaging_identity", "oro_organization_access",
                "oro_organization_sync", "oro_outbox_event", "oro_push_subscription", "oro_report");
        assertThat(tables).allSatisfy(t -> {
            assertThat(t.get("relrowsecurity")).isEqualTo(true);
            assertThat(t.get("owner")).isEqualTo("oro_owner");
        });
    }

    @Test
    void theApiRoleCannotBypassRlsNorTouchTheOrganizationAccesses() {
        assertThat(admin.queryForObject("SELECT rolbypassrls OR rolsuper FROM pg_roles WHERE rolname = 'oro_app'",
                Boolean.class)).isFalse();
        for (String login : List.of("oro_api", "oro_worker")) {
            assertThat(admin.queryForObject("SELECT rolbypassrls OR rolsuper OR rolinherit FROM pg_roles WHERE rolname = ?",
                    Boolean.class, login)).as(login).isFalse();
        }
        // the API's login is a member of oro_app only, the worker's of oro_system only
        String memberships = """
                SELECT r.rolname FROM pg_auth_members a JOIN pg_roles r ON r.oid = a.roleid WHERE a.member = ?::regrole""";
        assertThat(admin.queryForList(memberships, String.class, "oro_api")).containsExactly("oro_app");
        assertThat(admin.queryForList(memberships, String.class, "oro_worker")).containsExactly("oro_system");
        assertThat(admin.queryForObject("SELECT has_table_privilege('oro_app', 'oro_organization_access', 'SELECT')",
                Boolean.class)).isFalse();
        assertThat(admin.queryForObject("SELECT has_table_privilege('oro_app', 'oro_outbox_event', 'SELECT')",
                Boolean.class)).isFalse();
        assertThat(admin.queryForObject("SELECT has_column_privilege('oro_app', 'oro_message', 'sender_id', 'UPDATE')",
                Boolean.class)).isFalse();
    }

    @Test
    void theDevelopmentSeedHasBothProductsAndTheirContexts() throws Exception {
        for (int run = 0; run < 2; run++) {
            ExecResult result = psql("/scripts/oro/INSERT.sql");
            assertThat(result.getExitCode()).as(result.getStderr()).isZero();
        }
        assertThat(count("SELECT count(*) FROM oro_messaging_identity WHERE product_id = 'ORO'")).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM oro_messaging_identity WHERE product_id = 'UMOJA'")).isEqualTo(3);
        assertThat(count("SELECT count(*) FROM oro_conversation WHERE context_type = 'personal' AND conversation_type = 'group'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM oro_conversation WHERE context_type = 'personal' AND conversation_type = 'direct'")).isEqualTo(1);
        assertThat(count("SELECT count(DISTINCT organization_id) FROM oro_conversation WHERE context_type = 'organization'")).isEqualTo(2);
        assertThat(count("SELECT count(DISTINCT organization_id) FROM oro_organization_access")).isEqualTo(2);
        // run twice: no message twice, no gap
        assertThat(count("SELECT count(*) FROM oro_message")).isEqualTo(6);
        assertThat(count("SELECT sum(last_message_seq) FROM oro_conversation")).isEqualTo(6);
    }
}
