package dev.ltp.control.scenario;

import static dev.ltp.control.BrowserCsrf.browserCsrf;
import static dev.ltp.control.auth.AuthTestSupport.PASSWORD;
import static dev.ltp.control.auth.AuthTestSupport.TEST_LOGIN_PREFIX;
import static dev.ltp.control.auth.AuthTestSupport.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ltp.control.IntegrationTest;
import dev.ltp.control.auth.Role;
import dev.ltp.control.auth.UserService;
import java.util.HashMap;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * US-02: every saved change is a new immutable version; an identical script saves nothing.
 *
 * <p>Transactional (rolled back), like {@link ScenarioApiTests}: versions cannot be deleted. The
 * concurrent case needs committed data and lives in {@link ScenarioVersionConcurrencyTests}.
 */
@IntegrationTest
@Transactional
class ScenarioVersionApiTests {

    static final String V1 = """
            import http from 'k6/http';
            export default function () {
              http.get(__ENV.BASE_URL + '/');
            }
            """;

    static final String V2 = V1.replace("'/'", "'/cart'");

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper jsonMapper;

    @Autowired
    UserService userService;

    @Autowired
    ScenarioVersionRepository versions;

    MockHttpSession developer;
    long scenarioId;

    @BeforeEach
    void createScenarioWithVersion1() throws Exception {
        developer = signInAs(Role.DEVELOPER);
        String body = mvc.perform(post("/api/v1/scenarios")
                        .session(developer)
                        .with(browserCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Versioned", "script", V1))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        scenarioId = jsonMapper.readTree(body).path("id").asLong();
    }

    // --- US-02 acceptance criterion ------------------------------------------------------------------

    @Test
    void savingAChangedScriptCreatesVersion2AndLeavesVersion1UnchangedAndSelectable() throws Exception {
        ScenarioVersion v1Before = versions.findByScenarioIdAndVersion(scenarioId, 1).orElseThrow();

        save(developer, V2, 1)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/scenarios/" + scenarioId + "/versions/2"))
                .andExpect(jsonPath("$.created").value(true))
                .andExpect(jsonPath("$.version.version").value(2))
                .andExpect(jsonPath("$.version.script").value(V2))
                .andExpect(jsonPath("$.version.sha256").value(ScriptHash.sha256Hex(V2)));

        // Version 1 is exactly as it was
        ScenarioVersion v1After = versions.findByScenarioIdAndVersion(scenarioId, 1).orElseThrow();
        assertThat(v1After.getId()).isEqualTo(v1Before.getId());
        assertThat(v1After.getScript()).isEqualTo(V1);
        assertThat(v1After.getSha256()).isEqualTo(ScriptHash.sha256Hex(V1));
        assertThat(v1After.getCreatedAt()).isEqualTo(v1Before.getCreatedAt());

        // ... and still selectable
        mvc.perform(get(versionsUrl() + "/1").session(developer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.script").value(V1));
        mvc.perform(get(versionsUrl()).session(developer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].version", Matchers.contains(2, 1)))
                .andExpect(jsonPath("$[*].script").doesNotExist());

        // The scenario now points at version 2
        mvc.perform(get("/api/v1/scenarios/" + scenarioId).session(developer))
                .andExpect(jsonPath("$.latestVersion.version").value(2))
                .andExpect(jsonPath("$.latestVersion.script").value(V2));
        mvc.perform(get("/api/v1/scenarios").session(developer))
                .andExpect(jsonPath("$[?(@.id == " + scenarioId + ")].latestVersion").value(2));
    }

    // --- Extra checks: identical script, no update or delete -----------------------------------------

    @Test
    void savingTheIdenticalScriptCreatesNoVersion() throws Exception {
        save(developer, V1, 1)
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.created").value(false))
                .andExpect(jsonPath("$.version.version").value(1));

        assertThat(versions.findByScenarioIdOrderByVersionAsc(scenarioId)).hasSize(1);
    }

    @Test
    void theScriptMatchingTheLatestVersionSavesNothingEvenFromAnOlderBase() throws Exception {
        save(developer, V2, 1).andExpect(status().isCreated());

        // Based on v1, but the result is already what v2 holds: nothing to lose, nothing to save
        save(developer, V2, 1).andExpect(status().isOk()).andExpect(jsonPath("$.version.version").value(2));
        assertThat(versions.findByScenarioIdOrderByVersionAsc(scenarioId)).hasSize(2);
    }

    @Test
    void anyByteCountsAsAChange() throws Exception {
        save(developer, V1 + " ", 1).andExpect(status().isCreated()).andExpect(jsonPath("$.version.version").value(2));
        save(developer, (V1 + " ").replace("\n", "\r\n"), 2)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version.version").value(3));
    }

    @Test
    void goingBackToAnEarlierScriptIsANewVersion() throws Exception {
        save(developer, V2, 1).andExpect(status().isCreated());

        save(developer, V1, 2)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version.version").value(3))
                .andExpect(jsonPath("$.version.sha256").value(ScriptHash.sha256Hex(V1)));
    }

    @Test
    void versionsHaveNoUpdateOrDeleteEndpoint() throws Exception {
        String version1 = versionsUrl() + "/1";
        for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[] {
            put(version1), patch(version1), delete(version1), put(versionsUrl()), delete(versionsUrl())
        }) {
            mvc.perform(request.session(developer)
                            .with(browserCsrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("script", "changed"))))
                    .andExpect(status().isMethodNotAllowed());
        }
        assertThat(versions.findByScenarioIdAndVersion(scenarioId, 1).orElseThrow().getScript()).isEqualTo(V1);
    }

    // --- Stale edits (409) ---------------------------------------------------------------------------

    @Test
    void aSaveBasedOnAnOutdatedVersionIsRefused() throws Exception {
        save(developer, V2, 1).andExpect(status().isCreated());

        save(signInAs(Role.DEVELOPER), V1 + "// my change\n", 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("Your changes are based on version 1, but version 2 is the latest. "
                                + "Load it and apply your changes again."));
        assertThat(versions.findByScenarioIdOrderByVersionAsc(scenarioId)).hasSize(2);
    }

    // --- Who saves -----------------------------------------------------------------------------------

    @Test
    void theNewVersionNamesWhoSavedIt() throws Exception {
        MockHttpSession admin = login(mvc, IntegrationTest.ADMIN_LOGIN, IntegrationTest.ADMIN_PASSWORD);

        save(admin, V2, 1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version.createdBy").value(IntegrationTest.ADMIN_LOGIN));
        mvc.perform(get("/api/v1/scenarios/" + scenarioId).session(admin))
                .andExpect(jsonPath("$.createdBy").value(Matchers.startsWith(TEST_LOGIN_PREFIX + "developer-")));
    }

    @Test
    void viewerCannotSaveButMayReadVersions() throws Exception {
        MockHttpSession viewer = signInAs(Role.VIEWER);

        save(viewer, V2, 1)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Your role does not allow this action."));
        assertThat(versions.findByScenarioIdOrderByVersionAsc(scenarioId)).hasSize(1);

        mvc.perform(get(versionsUrl()).session(viewer)).andExpect(status().isOk());
        mvc.perform(get(versionsUrl() + "/1").session(viewer)).andExpect(status().isOk());
    }

    // --- Validation and unknown ids ------------------------------------------------------------------

    @Test
    void emptyOrTooLargeScriptAndMissingBaseVersionAre400() throws Exception {
        save(developer, "  \n ", 1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("script"));
        save(developer, "// " + "x".repeat(ScenarioVersion.MAX_SCRIPT_BYTES - 2), 1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("script"))
                .andExpect(jsonPath("$.errors[0].message").value("must be at most 1 MB (1048576 bytes in UTF-8)"));

        Map<String, Object> noBase = new HashMap<>();
        noBase.put("script", V2);
        mvc.perform(post(versionsUrl())
                        .session(developer)
                        .with(browserCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(noBase)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("basedOnVersion"));

        assertThat(versions.findByScenarioIdOrderByVersionAsc(scenarioId)).hasSize(1);
    }

    @Test
    void unknownScenarioOrVersionIs404() throws Exception {
        String unknown = "/api/v1/scenarios/999999999/versions";
        mvc.perform(post(unknown)
                        .session(developer)
                        .with(browserCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("script", V2, "basedOnVersion", 1))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No scenario with id 999999999"));
        mvc.perform(get(unknown).session(developer)).andExpect(status().isNotFound());
        mvc.perform(get(unknown + "/1").session(developer)).andExpect(status().isNotFound());

        mvc.perform(get(versionsUrl() + "/5").session(developer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Scenario " + scenarioId + " has no version 5"));
    }

    // --- Helpers -------------------------------------------------------------------------------------

    String versionsUrl() {
        return "/api/v1/scenarios/" + scenarioId + "/versions";
    }

    ResultActions save(MockHttpSession session, String script, int basedOnVersion) throws Exception {
        return mvc.perform(post(versionsUrl())
                .session(session)
                .with(browserCsrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("script", script, "basedOnVersion", basedOnVersion))));
    }

    MockHttpSession signInAs(Role role) throws Exception {
        String login = TEST_LOGIN_PREFIX + role.name().toLowerCase() + "-" + System.nanoTime();
        userService.create(login, null, PASSWORD, role);
        return login(mvc, login, PASSWORD);
    }

    String json(Map<String, Object> body) {
        return jsonMapper.writeValueAsString(body);
    }
}
