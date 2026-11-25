package dev.ltp.control.scenario;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScenarioRepository extends JpaRepository<Scenario, Long> {

    Optional<Scenario> findByNameIgnoreCase(String name);
}
