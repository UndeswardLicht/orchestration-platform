package dev.ltp.control.scenario;

import dev.ltp.control.auth.User;
import dev.ltp.control.auth.UserRepository;
import dev.ltp.control.config.ApiException;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scenarios and their first version (US-01). The script is stored exactly as sent: no trimming and no
 * line-ending changes, so its SHA-256 always matches the stored text and a run can be reproduced
 * byte for byte.
 */
@Service
public class ScenarioService {

    private final ScenarioRepository scenarios;
    private final ScenarioVersionRepository versions;
    private final UserRepository users;

    ScenarioService(ScenarioRepository scenarios, ScenarioVersionRepository versions, UserRepository users) {
        this.scenarios = scenarios;
        this.versions = versions;
        this.users = users;
    }

    /**
     * Creates a scenario with version 1 of its script. Name and description lose surrounding spaces; an
     * empty description is stored as none. Names are unique ignoring case (409 otherwise).
     */
    @Transactional
    public ScenarioSummary create(String name, String description, String script, long creatorId) {
        String cleanName = name.strip();
        String cleanDescription = description == null || description.isBlank() ? null : description.strip();
        if (ScriptHash.utf8Length(script) > ScenarioVersion.MAX_SCRIPT_BYTES) {
            throw ApiException.badRequest(
                    "script", "must be at most 1 MB (" + ScenarioVersion.MAX_SCRIPT_BYTES + " bytes in UTF-8)");
        }
        if (scenarios.findByNameIgnoreCase(cleanName).isPresent()) {
            throw nameTaken(cleanName);
        }
        User creator = users.findById(creatorId)
                .orElseThrow(() -> ApiException.unauthorized("Your account no longer exists. Sign in again."));
        try {
            Scenario scenario = scenarios.saveAndFlush(new Scenario(cleanName, cleanDescription, creator));
            ScenarioVersion first =
                    versions.saveAndFlush(new ScenarioVersion(scenario, 1, script, ScriptHash.sha256Hex(script), creator));
            return new ScenarioSummary(
                    scenario.getId(),
                    scenario.getName(),
                    scenario.getDescription(),
                    first.getVersion(),
                    creator.getLogin(),
                    scenario.getCreatedAt(),
                    first.getCreatedAt());
        } catch (DataIntegrityViolationException e) {
            // Another request created the same name in the meantime (unique index scenarios_name_uq)
            throw nameTaken(cleanName);
        }
    }

    @Transactional(readOnly = true)
    public List<ScenarioSummary> list() {
        return scenarios.findAllSummaries();
    }

    @Transactional(readOnly = true)
    public ScenarioDetails get(long id) {
        Scenario scenario = scenarios.findById(id).orElseThrow(() -> notFound(id));
        ScenarioVersion latest =
                versions.findTopByScenarioIdOrderByVersionDesc(id).orElseThrow(() -> notFound(id));
        return ScenarioDetails.of(scenario, latest);
    }

    private static ApiException nameTaken(String name) {
        return ApiException.conflict("A scenario named '" + name + "' already exists");
    }

    private static ApiException notFound(long id) {
        return ApiException.notFound("No scenario with id " + id);
    }
}
