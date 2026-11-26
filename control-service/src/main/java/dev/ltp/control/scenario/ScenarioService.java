package dev.ltp.control.scenario;

import dev.ltp.control.auth.User;
import dev.ltp.control.auth.UserRepository;
import dev.ltp.control.config.ApiException;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scenarios and their immutable versions (US-01, US-02). The script is stored exactly as sent: no
 * trimming and no line-ending changes, so its SHA-256 always matches the stored text and a run can be
 * reproduced byte for byte. Versions are only ever added, never changed.
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
        checkScriptSize(script);
        if (scenarios.findByNameIgnoreCase(cleanName).isPresent()) {
            throw nameTaken(cleanName);
        }
        User creator = user(creatorId);
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

    /**
     * Saves a changed script as the next version (US-02). Only the latest version is compared: a script
     * identical to it saves nothing and returns it ({@code created = false}); going back to an older
     * script is a new version. {@code basedOnVersion} is the version the user edited; if a newer one was
     * saved meanwhile, the save is refused (409) so nobody drops a colleague's change unseen.
     */
    @Transactional
    public SavedVersion saveVersion(long scenarioId, String script, int basedOnVersion, long creatorId) {
        checkScriptSize(script);
        // Locked: concurrent saves of this scenario wait here, then see the version saved before them
        Scenario scenario = scenarios.lockById(scenarioId).orElseThrow(() -> notFound(scenarioId));
        ScenarioVersion latest =
                versions.findTopByScenarioIdOrderByVersionDesc(scenarioId).orElseThrow(() -> notFound(scenarioId));
        String sha256 = ScriptHash.sha256Hex(script);
        if (sha256.equals(latest.getSha256())) {
            return new SavedVersion(false, ScenarioDetails.Version.of(latest));
        }
        if (basedOnVersion != latest.getVersion()) {
            throw ApiException.conflict("Your changes are based on version " + basedOnVersion + ", but version "
                    + latest.getVersion() + " is the latest. Load it and apply your changes again.");
        }
        User creator = user(creatorId);
        try {
            ScenarioVersion saved = versions.saveAndFlush(new ScenarioVersion(
                    scenario, latest.getVersion() + 1, script, sha256, creator));
            return new SavedVersion(true, ScenarioDetails.Version.of(saved));
        } catch (DataIntegrityViolationException e) {
            // Safety net behind the lock: unique (scenario_id, version)
            throw ApiException.conflict("Another version of this scenario was saved at the same time. "
                    + "Load the latest version and apply your changes again.");
        }
    }

    @Transactional(readOnly = true)
    public List<ScenarioVersionSummary> listVersions(long scenarioId) {
        requireScenario(scenarioId);
        return versions.findSummaries(scenarioId);
    }

    @Transactional(readOnly = true)
    public ScenarioDetails.Version getVersion(long scenarioId, int version) {
        requireScenario(scenarioId);
        return versions.findByScenarioIdAndVersion(scenarioId, version)
                .map(ScenarioDetails.Version::of)
                .orElseThrow(() -> ApiException.notFound("Scenario " + scenarioId + " has no version " + version));
    }

    private void requireScenario(long scenarioId) {
        if (!scenarios.existsById(scenarioId)) {
            throw notFound(scenarioId);
        }
    }

    private User user(long id) {
        return users.findById(id)
                .orElseThrow(() -> ApiException.unauthorized("Your account no longer exists. Sign in again."));
    }

    private static void checkScriptSize(String script) {
        if (ScriptHash.utf8Length(script) > ScenarioVersion.MAX_SCRIPT_BYTES) {
            throw ApiException.badRequest(
                    "script", "must be at most 1 MB (" + ScenarioVersion.MAX_SCRIPT_BYTES + " bytes in UTF-8)");
        }
    }

    private static ApiException nameTaken(String name) {
        return ApiException.conflict("A scenario named '" + name + "' already exists");
    }

    private static ApiException notFound(long id) {
        return ApiException.notFound("No scenario with id " + id);
    }
}
