package dev.ltp.control.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ltp.control.IntegrationTest;
import dev.ltp.control.auth.UserRepository;
import dev.ltp.control.config.ApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

/**
 * US-02: two saves based on the same version at the same moment. The scenario row lock makes them take
 * turns, so exactly one becomes version 2 and the other is refused (409) instead of silently becoming
 * version 3 or failing with a database error.
 *
 * <p>Not transactional: the two saves must run in their own committed transactions to meet at the lock.
 * Versions cannot be deleted, so the scenario stays in the test database. It belongs to the seeded admin
 * and has a unique name outside the {@code it-} prefix, so no other test sees or cleans it up.
 */
@IntegrationTest
class ScenarioVersionConcurrencyTests {

    @Autowired
    ScenarioService scenarioService;

    @Autowired
    ScenarioVersionRepository versions;

    @Autowired
    UserRepository users;

    @Test
    void twoSavesFromTheSameVersionGiveOneNewVersionAndOneConflict() throws Exception {
        long adminId = users.findActiveByLogin(IntegrationTest.ADMIN_LOGIN).orElseThrow().getId();
        long scenarioId = scenarioService
                .create("concurrency-" + System.nanoTime(), null, "// v1\n", adminId)
                .id();

        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Object>> saves = new ArrayList<>();
        for (String script : List.of("// change A\n", "// change B\n")) {
            saves.add(() -> {
                start.await();
                try {
                    return scenarioService.saveVersion(scenarioId, script, 1, adminId);
                } catch (ApiException e) {
                    return e;
                }
            });
        }

        List<Object> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<Object>> futures = saves.stream().map(pool::submit).toList();
            start.countDown();
            for (Future<Object> future : futures) {
                results.add(future.get());
            }
        }

        assertThat(results).filteredOn(SavedVersion.class::isInstance).singleElement().satisfies(saved -> {
            assertThat(((SavedVersion) saved).created()).isTrue();
            assertThat(((SavedVersion) saved).version().version()).isEqualTo(2);
        });
        assertThat(results).filteredOn(ApiException.class::isInstance).singleElement().satisfies(refused ->
                assertThat(((ApiException) refused).status()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(versions.findByScenarioIdOrderByVersionAsc(scenarioId)).extracting(ScenarioVersion::getVersion)
                .containsExactly(1, 2);
    }
}
