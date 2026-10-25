package com.asata.org.oro.schema;

import com.asata.org.oro.PostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container.ExecResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The runner of the schema (oro/migrate.sh), in a database of its own in the container (the one of the other tests is
 * left as it is): a new database built by the schema, migrations applied once and recorded, a migration changed after
 * it was applied refused, the migrations of before recorded as included in a new database.
 */
class MigrationTest extends PostgresIntegrationTest {

    private static final String DATABASE = "migrate_test";

    @BeforeEach
    void emptyDatabase() throws Exception {
        sql("postgres", "DROP DATABASE IF EXISTS " + DATABASE);
        sql("postgres", "CREATE DATABASE " + DATABASE);
        // the accounts of Oro need the tables of Asata, created before Oro's
        ExecResult asata = shell("psql -q -v ON_ERROR_STOP=1 -U " + POSTGRES.getUsername() + " -d " + DATABASE
                + " -f /scripts/CREATE_ASATA_TABLES.sql");
        assertThat(asata.getExitCode()).as(asata.getStderr()).isZero();
        shell("rm -rf /tmp/migrations && mkdir -p /tmp/migrations");
    }

    @AfterEach
    void dropDatabase() throws Exception {
        sql("postgres", "DROP DATABASE IF EXISTS " + DATABASE);
    }

    private static ExecResult shell(String command) throws Exception {
        return POSTGRES.execInContainer("sh", "-c", command);
    }

    private static String sql(String database, String statement) throws Exception {
        ExecResult result = POSTGRES.execInContainer("psql", "-X", "-At", "-v", "ON_ERROR_STOP=1", "-U", POSTGRES.getUsername(),
                "-d", database, "-c", statement);
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
        return result.getStdout().strip();
    }

    private static ExecResult migrate() throws Exception {
        return shell("ORO_MIGRATIONS_DIR=/tmp/migrations PSQL_ARGS='-U " + POSTGRES.getUsername() + " -d " + DATABASE
                + "' sh /scripts/oro/migrate.sh");
    }

    private static void migration(String name, String body) throws Exception {
        shell("cat > /tmp/migrations/" + name + " <<'SQL'\n" + body + "\nSQL");
    }

    @Test
    void aNewDatabaseThenAMigrationAppliedOnce() throws Exception {
        ExecResult first = migrate();
        assertThat(first.getExitCode()).as(first.getStderr()).isZero();
        assertThat(first.getStdout()).contains("a new database", "done (0 applied)");
        assertThat(sql(DATABASE, "SELECT count(*) FROM pg_tables WHERE tablename LIKE 'oro\\_%'")).isEqualTo("15");
        assertThat(sql(DATABASE, "SELECT pg_get_userbyid(relowner) || ' ' || relrowsecurity FROM pg_class WHERE relname = 'oro_schema_version'"))
                .isEqualTo("oro_owner true");

        // an existing database: the migration once, recorded; the schema again after it
        migration("V2__probe_column.sql", "ALTER TABLE oro_conversation ADD COLUMN IF NOT EXISTS probe TEXT;");
        migration("V10__probe_index.sql", "CREATE INDEX IF NOT EXISTS idx_probe ON oro_conversation(probe);");
        ExecResult second = migrate();
        assertThat(second.getExitCode()).as(second.getStderr()).isZero();
        // in the order of their numbers, not of their names (V10 after V2)
        assertThat(second.getStdout()).containsSubsequence("V2__probe_column", "V10__probe_index", "done (2 applied), at version 10");
        assertThat(sql(DATABASE, "SELECT string_agg(version || ':' || name, ',' ORDER BY version) FROM oro_schema_version"))
                .isEqualTo("2:V2__probe_column,10:V10__probe_index");

        ExecResult again = migrate();
        assertThat(again.getStdout()).contains("done (0 applied)");
    }

    @Test
    void aMigrationChangedOrFailingStopsTheRunner() throws Exception {
        migrate();
        migration("V2__probe_column.sql", "ALTER TABLE oro_conversation ADD COLUMN IF NOT EXISTS probe TEXT;");
        assertThat(migrate().getExitCode()).isZero();

        migration("V2__probe_column.sql", "ALTER TABLE oro_conversation ADD COLUMN IF NOT EXISTS probe2 TEXT;");
        ExecResult changed = migrate();
        assertThat(changed.getExitCode()).isNotZero();
        assertThat(changed.getStderr()).contains("changed after it was applied");

        // a failing migration leaves nothing of it, and is not recorded
        migration("V2__probe_column.sql", "ALTER TABLE oro_conversation ADD COLUMN IF NOT EXISTS probe TEXT;");
        migration("V3__half_done.sql", "ALTER TABLE oro_conversation ADD COLUMN half TEXT;\nSELECT 1 / 0;");
        assertThat(migrate().getExitCode()).isNotZero();
        assertThat(sql(DATABASE, "SELECT count(*) FROM information_schema.columns WHERE column_name = 'half'")).isEqualTo("0");
        assertThat(sql(DATABASE, "SELECT max(version) FROM oro_schema_version")).isEqualTo("2");
    }

    @Test
    void aNewDatabaseRecordsTheMigrationsOfBeforeAsIncluded() throws Exception {
        // the schema of now holds what the migrations did: a new database records them, never runs them
        migration("V2__never_run.sql", "SELECT 1 / 0;");
        ExecResult fresh = migrate();
        assertThat(fresh.getExitCode()).as(fresh.getStderr()).isZero();
        assertThat(sql(DATABASE, "SELECT name FROM oro_schema_version")).isEqualTo("V2__never_run");
    }
}
