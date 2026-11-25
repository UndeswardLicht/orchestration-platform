package dev.ltp.control;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ltp.control.admin.SettingKeys;
import dev.ltp.control.admin.SettingRepository;
import dev.ltp.control.auth.Role;
import dev.ltp.control.auth.User;
import dev.ltp.control.auth.UserRepository;
import dev.ltp.control.scenario.Scenario;
import dev.ltp.control.scenario.ScenarioRepository;
import dev.ltp.control.scenario.ScenarioVersion;
import dev.ltp.control.scenario.ScenarioVersionRepository;
import dev.ltp.control.worker.Worker;
import dev.ltp.control.worker.WorkerRepository;
import dev.ltp.control.worker.WorkerStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * The JPA entities save to and load from the V1 tables (Hibernate's {@code ddl-auto=validate} already
 * checked that they match when the context started). Every test is rolled back.
 */
@IntegrationTest
@Transactional
class EntityMappingTests {

    static final String HASH = "{bcrypt}$2a$10$abcdefghijklmnopqrstuv";

    @Autowired
    EntityManager em;

    @Autowired
    UserRepository users;

    @Autowired
    ScenarioRepository scenarios;

    @Autowired
    ScenarioVersionRepository versions;

    @Autowired
    WorkerRepository workers;

    @Autowired
    SettingRepository settings;

    @Test
    void userRoundTripAndCaseInsensitiveLookup() {
        users.save(new User("Dev.One", "dev@example.org", HASH, Role.DEVELOPER));
        em.flush();
        em.clear();

        User loaded = users.findActiveByLogin("dev.one").orElseThrow();
        assertThat(loaded.getRole()).isEqualTo(Role.DEVELOPER);
        assertThat(loaded.getEmail()).isEqualTo("dev@example.org");
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.isDeleted()).isFalse();
    }

    @Test
    void scenarioWithVersionsRoundTrip() {
        User author = users.save(new User("author", null, HASH, Role.DEVELOPER));
        Scenario scenario = scenarios.save(new Scenario("Checkout API smoke", "Smoke test", author));
        versions.save(new ScenarioVersion(scenario, 1, "export default function () {}", "a".repeat(64), author));
        versions.save(new ScenarioVersion(scenario, 2, "export default function () { /* v2 */ }", "b".repeat(64), author));
        em.flush();
        em.clear();

        assertThat(scenarios.findByNameIgnoreCase("checkout api SMOKE")).isPresent();
        ScenarioVersion latest = versions.findTopByScenarioIdOrderByVersionDesc(scenario.getId()).orElseThrow();
        assertThat(latest.getVersion()).isEqualTo(2);
        assertThat(latest.getScript()).contains("v2");
        assertThat(latest.getCreatedBy().getLogin()).isEqualTo("author");
        assertThat(versions.findByScenarioIdOrderByVersionAsc(scenario.getId()))
                .extracting(ScenarioVersion::getVersion)
                .containsExactly(1, 2);
        assertThat(versions.findByScenarioIdAndVersion(scenario.getId(), 1).orElseThrow().getScript())
                .isEqualTo("export default function () {}");
    }

    @Test
    void workerRoundTripStartsOnline() {
        workers.save(new Worker("worker-a", 2.0, 4096, "0.1.0", "secret-hash"));
        em.flush();
        em.clear();

        Worker loaded = workers.findByName("worker-a").orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(WorkerStatus.ONLINE);
        assertThat(loaded.getCpus()).isEqualTo(2.0);
        assertThat(loaded.getMemMb()).isEqualTo(4096);
        assertThat(loaded.getLastHeartbeatAt()).isEqualTo(loaded.getRegisteredAt());
        assertThat(workers.findByStatus(WorkerStatus.ONLINE)).extracting(Worker::getName).contains("worker-a");
    }

    @Test
    void settingsAreReadableThroughTheEntity() {
        assertThat(settings.findById(SettingKeys.MAX_RPS).orElseThrow().getValue()).isEqualTo("1000");
        assertThat(settings.findById(SettingKeys.MAX_DURATION_SEC).orElseThrow().getValue()).isEqualTo("1800");
        assertThat(settings.findById(SettingKeys.K6_CPU).orElseThrow().getValue()).isEqualTo("1.0");
        assertThat(settings.findById(SettingKeys.K6_MEM_MB).orElseThrow().getValue()).isEqualTo("512");
        assertThat(settings.findById(SettingKeys.WARMUP_SEC).orElseThrow().getValue()).isEqualTo("10");
        assertThat(settings.findById(SettingKeys.MAX_RPS).orElseThrow().getUpdatedBy()).isNull();
    }
}
