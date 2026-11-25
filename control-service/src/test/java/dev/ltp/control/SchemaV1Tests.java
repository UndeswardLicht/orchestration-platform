package dev.ltp.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.sql.Savepoint;
import java.util.HashMap;
import java.util.Map;
import org.assertj.core.api.AbstractThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * The rules the V1 migration puts into the database itself (T-03). Plain SQL on purpose: these rules
 * must hold even for code that bypasses the JPA entities. Every test is rolled back.
 */
@IntegrationTest
@Transactional
class SchemaV1Tests {

    static final String SHA = "a".repeat(64);
    static final String HASH = "{bcrypt}$2a$10$abcdefghijklmnopqrstuv";

    @Autowired
    JdbcTemplate jdbc;

    long userId;
    long scenarioId;

    @BeforeEach
    void createUserAndScenario() {
        userId = insertUser("schema-test-user", "DEVELOPER");
        scenarioId = insertScenario("Schema test scenario");
    }

    @Test
    void migrationV1IsApplied() {
        var applied = jdbc.queryForList("select version from flyway_schema_history where success", String.class);
        assertThat(applied).containsExactly("1");
        var tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'", String.class);
        assertThat(tables)
                .contains("users", "scenarios", "scenario_versions", "workers", "settings")
                .doesNotContain("runs", "api_tokens", "targets");
    }

    // --- scenario_versions: immutable (US-02) -----------------------------------------------------

    @Test
    void scenarioVersionCannotBeUpdated() {
        long versionId = insertVersion(1, "export default function () {}");

        assertRejected(() -> jdbc.update("update scenario_versions set script = 'changed' where id = ?", versionId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("scenario versions are immutable (UPDATE");
    }

    @Test
    void scenarioVersionCannotBeDeleted() {
        long versionId = insertVersion(1, "export default function () {}");

        assertRejected(() -> jdbc.update("delete from scenario_versions where id = ?", versionId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("scenario versions are immutable (DELETE");
    }

    @Test
    void scenarioVersionsCannotBeTruncated() {
        insertVersion(1, "export default function () {}");

        assertRejected(() -> jdbc.execute("truncate scenario_versions"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("scenario versions are immutable (TRUNCATE");
    }

    @Test
    void versionNumberIsUniquePerScenario() {
        insertVersion(1, "v1");
        long otherScenario = insertScenario("Other scenario");

        assertRejected(() -> insertVersion(1, "v1 again"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("scenario_versions_scenario_version_uq");
        assertThatCode(() -> insertVersion(otherScenario, 1, "v1 of another scenario")).doesNotThrowAnyException();
    }

    @Test
    void sameScriptMayComeBackAsANewVersion() {
        // v1 = A, v2 = B, v3 = A again: going back is a legitimate new version (US-02 compares the latest only)
        insertVersion(1, "A");
        insertVersion(2, "B");
        assertThatCode(() -> insertVersion(3, "A")).doesNotThrowAnyException();
    }

    @Test
    void scriptIsLimitedTo1MbAndMustNotBeEmpty() {
        String exactly1Mb = "x".repeat(1024 * 1024);
        assertThatCode(() -> insertVersion(1, exactly1Mb)).doesNotThrowAnyException();

        assertRejected(() -> insertVersion(2, exactly1Mb + "x")).isInstanceOf(DataIntegrityViolationException.class);
        assertRejected(() -> insertVersion(3, "")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void scriptLimitCountsBytesNotCharacters() {
        // "é" is 2 bytes in UTF-8, so 600 000 of them are 1.2 MB although only 600 000 characters
        assertRejected(() -> insertVersion(1, "é".repeat(600_000)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sha256MustBeLowerCaseHex() {
        assertRejected(() -> jdbc.update(
                        "insert into scenario_versions (scenario_id, version, script, sha256, created_by) values (?, 1, 's', ?, ?)",
                        scenarioId, "A".repeat(64), userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- scenarios: names unique ignoring case ----------------------------------------------------

    @Test
    void scenarioNameIsUniqueIgnoringCase() {
        assertRejected(() -> insertScenario("SCHEMA TEST SCENARIO"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("scenarios_name_uq");
    }

    @Test
    void scenarioNameMustNotBeBlank() {
        assertRejected(() -> insertScenario("   ")).isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- users ------------------------------------------------------------------------------------

    @Test
    void onlyKnownRolesAreAllowed() {
        assertRejected(() -> insertUser("someone", "SUPERUSER")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void loginIsUniqueIgnoringCase() {
        assertRejected(() -> insertUser("Schema-Test-User", "VIEWER"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("users_login_uq");
    }

    @Test
    void loginOfADeletedAccountCanBeReused() {
        jdbc.update("update users set deleted_at = now() where id = ?", userId);

        assertThatCode(() -> insertUser("schema-test-user", "VIEWER")).doesNotThrowAnyException();
    }

    @Test
    void passwordMustBeASaltedHashWithAlgorithmPrefix() {
        // NFR-05: only {bcrypt} / {argon2...} values; plain text and unsalted algorithms are rejected
        for (String bad : new String[] {"secret", "{noop}secret", "{sha256}abc", "{bcrypt}"}) {
            assertRejected(() -> jdbc.update(
                            "insert into users (login, password_hash, role) values (?, ?, 'VIEWER')", "u-" + bad.length(), bad))
                    .as(bad)
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThatCode(() -> jdbc.update(
                        "insert into users (login, password_hash, role) values ('argon-user', ?, 'VIEWER')",
                        "{argon2@SpringSecurity_v5_8}$argon2id$v=19$m=16384,t=2,p=1$abc$def"))
                .doesNotThrowAnyException();
    }

    // --- workers ----------------------------------------------------------------------------------

    @Test
    void workerNameIsUnique() {
        insertWorker("worker-1", "ONLINE");

        assertRejected(() -> insertWorker("worker-1", "ONLINE")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void onlyKnownWorkerStatusesAreAllowed() {
        assertRejected(() -> insertWorker("worker-2", "SLEEPING")).isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- settings ---------------------------------------------------------------------------------

    @Test
    void settingsStartWithTheNfrDefaults() {
        Map<String, String> settings = new HashMap<>();
        jdbc.query("select key, value from settings", rs -> {
            settings.put(rs.getString("key"), rs.getString("value"));
        });

        assertThat(settings)
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "max_rps", "1000", // NFR-06
                        "max_duration_sec", "1800", // NFR-06: 30 min
                        "k6_cpu", "1.0", // NFR-05
                        "k6_mem_mb", "512", // NFR-05
                        "warmup_sec", "10")); // evaluation plan: W = 10 s
    }

    // --- helpers ----------------------------------------------------------------------------------

    /**
     * Runs a statement the database should reject, inside a savepoint. PostgreSQL aborts the whole
     * transaction after a failed statement; rolling back to the savepoint undoes only this one, so the
     * test can go on.
     */
    AbstractThrowableAssert<?, ? extends Throwable> assertRejected(Runnable statement) {
        DataAccessException rejection = jdbc.execute((ConnectionCallback<DataAccessException>) con -> {
            Savepoint savepoint = con.setSavepoint();
            try {
                statement.run();
                con.releaseSavepoint(savepoint);
                return null;
            } catch (DataAccessException e) {
                con.rollback(savepoint);
                return e;
            }
        });
        return assertThat(rejection).as("the database should reject the statement").isNotNull();
    }

    long insertUser(String login, String role) {
        return jdbc.queryForObject(
                "insert into users (login, password_hash, role) values (?, ?, ?) returning id", Long.class, login, HASH, role);
    }

    long insertScenario(String name) {
        return jdbc.queryForObject(
                "insert into scenarios (name, created_by) values (?, ?) returning id", Long.class, name, userId);
    }

    long insertVersion(int version, String script) {
        return insertVersion(scenarioId, version, script);
    }

    long insertVersion(long scenario, int version, String script) {
        return jdbc.queryForObject(
                "insert into scenario_versions (scenario_id, version, script, sha256, created_by)"
                        + " values (?, ?, ?, ?, ?) returning id",
                Long.class, scenario, version, script, SHA, userId);
    }

    void insertWorker(String name, String status) {
        jdbc.update(
                "insert into workers (name, status, cpus, mem_mb, version, secret_hash, last_heartbeat_at)"
                        + " values (?, ?, 2, 4096, 'dev', 'hash', now())",
                name, status);
    }
}
