package dev.ltp.control.scenario;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ScenarioRepository extends JpaRepository<Scenario, Long> {

    Optional<Scenario> findByNameIgnoreCase(String name);

    /**
     * The scenario, locked until the end of the transaction (US-02). Saves of new versions of one
     * scenario take turns, so each one sees the version saved before it.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Scenario s where s.id = :id")
    Optional<Scenario> lockById(long id);

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
