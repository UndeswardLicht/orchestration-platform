package dev.ltp.control.scenario;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ScenarioVersionRepository extends JpaRepository<ScenarioVersion, Long> {

    Optional<ScenarioVersion> findTopByScenarioIdOrderByVersionDesc(Long scenarioId);

    Optional<ScenarioVersion> findByScenarioIdAndVersion(Long scenarioId, int version);

    List<ScenarioVersion> findByScenarioIdOrderByVersionAsc(Long scenarioId);

    /** The version list of a scenario (US-02), newest first, without loading the scripts. */
    @Query("""
            select new dev.ltp.control.scenario.ScenarioVersionSummary(v.id, v.version, v.sha256, u.login, v.createdAt)
            from ScenarioVersion v join v.createdBy u
            where v.scenario.id = :scenarioId
            order by v.version desc""")
    List<ScenarioVersionSummary> findSummaries(long scenarioId);
}
