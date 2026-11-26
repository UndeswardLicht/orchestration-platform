package dev.ltp.control.scenario;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ScenarioRepository extends JpaRepository<Scenario, Long> {

    Optional<Scenario> findByNameIgnoreCase(String name);

    /**
     * The scenario list (US-01): every scenario with its newest version, ordered by name ignoring case.
     * One query, without loading the scripts.
     */
    @Query("""
            select new dev.ltp.control.scenario.ScenarioSummary(
                s.id, s.name, s.description, v.version, u.login, s.createdAt, v.createdAt)
            from ScenarioVersion v join v.scenario s join s.createdBy u
            where v.version = (select max(w.version) from ScenarioVersion w where w.scenario = s)
            order by lower(s.name), s.id""")
    List<ScenarioSummary> findAllSummaries();
}
