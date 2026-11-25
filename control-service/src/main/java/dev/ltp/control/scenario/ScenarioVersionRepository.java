package dev.ltp.control.scenario;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScenarioVersionRepository extends JpaRepository<ScenarioVersion, Long> {

    Optional<ScenarioVersion> findTopByScenarioIdOrderByVersionDesc(Long scenarioId);

    Optional<ScenarioVersion> findByScenarioIdAndVersion(Long scenarioId, int version);

    List<ScenarioVersion> findByScenarioIdOrderByVersionAsc(Long scenarioId);
}
