package dev.ltp.control.scenario;

import static dev.ltp.control.BrowserCsrf.browserCsrf;
import static dev.ltp.control.auth.AuthTestSupport.PASSWORD;
import static dev.ltp.control.auth.AuthTestSupport.TEST_LOGIN_PREFIX;
import static dev.ltp.control.auth.AuthTestSupport.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ltp.control.IntegrationTest;
import dev.ltp.control.auth.Role;
import dev.ltp.control.auth.UserService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * US-01: create a scenario from a k6 script and see it in the list.
 *
 * <p>Transactional: every test is rolled back. Scenario versions cannot be deleted (database trigger),
 * so rollback is the only way to leave the shared database clean. MockMvc runs in the test thread, so
 * the requests take part in the test transaction.
 */
@IntegrationTest
@Transactional
class ScenarioApiTests {

    static final String SCRIPT = """
            import http from 'k6/http';
            export default function () {
              http.get(__ENV.BASE_URL + '/');
            }
            """;

    static final int MAX = ScenarioVersion.MAX_SCRIPT_BYTES;

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper jsonMapper;

    @Autowired
    UserService userService;

    @Autowired
    ScenarioVersionRepository versions;

    @Autowired
    ScenarioRepository scenarios;

    // --- US-01 acceptance criterion ------------------------------------------------------------------

    @Test
    void developerCreatesScenarioWithVersion1AndSeesItInTheList() throws Exception {
        MockHttpSession developer = signInAs(Role.DEVELOPER);

        long id = idOf(create(developer, Map.of("name", "Checkout API", "description", "Pay", "script", SCRIPT))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("/api/v1/scenarios/\\d+")))
                .andExpect(jsonPath("$.name").value("Checkout API"))
                .andExpect(jsonPath("$.latestVersion").value(1)));

        ScenarioVersion v1 = versions.findByScenarioIdAndVersion(id, 1).orElseThrow();
        assertThat(v1.getScript()).isEqualTo(SCRIPT);
        assertThat(v1.getSha256()).isEqualTo(ScriptHash.sha256Hex(SCRIPT));
        assertThat(versions.findByScenarioIdOrderByVersionAsc(id)).hasSize(1);

        mvc.perform(get("/api/v1/scenarios").session(developer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].name").value("Checkout API"))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].latestVersion").value(1))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].description").value("Pay"))
                .andExpect(jsonPath("$[*].script").doesNotExist());
    }

    @Test
    void theNewScenarioCanBeOpenedWithItsScript() throws Exception {
        MockHttpSession developer = signInAs(Role.DEVELOPER);
        long id = idOf(create(developer, scenario("Checkout API")).andExpect(status().isCreated()));

        mvc.perform(get("/api/v1/scenarios/" + id).session(developer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Checkout API"))
                .andExpect(jsonPath("$.createdBy").value(matchesPattern(TEST_LOGIN_PREFIX + "developer-.*")))
                .andExpect(jsonPath("$.latestVersion.version").value(1))
                .andExpect(jsonPath("$.latestVersion.script").value(SCRIPT))
                .andExpect(jsonPath("$.latestVersion.sha256").value(ScriptHash.sha256Hex(SCRIPT)));
    }

    // --- Roles (US-23) -------------------------------------------------------------------------------

    @Test
    void adminMayCreateScenarios() throws Exception {
        MockHttpSession admin = login(mvc, IntegrationTest.ADMIN_LOGIN, IntegrationTest.ADMIN_PASSWORD);

        create(admin, scenario("Admin scenario"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdBy").value(IntegrationTest.ADMIN_LOGIN));
    }

    @Test
    void viewerCannotCreateAndNothingIsSaved() throws Exception {
        MockHttpSession viewer = signInAs(Role.VIEWER);

        create(viewer, scenario("Viewer scenario"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Your role does not allow this action."));

        assertThat(scenarios.findByNameIgnoreCase("Viewer scenario")).isEmpty();
    }

    @Test
    void viewerMayReadTheListAndOpenAScenario() throws Exception {
        long id = idOf(create(signInAs(Role.DEVELOPER), scenario("Readable")).andExpect(status().isCreated()));
        MockHttpSession viewer = signInAs(Role.VIEWER);

        mvc.perform(get("/api/v1/scenarios").session(viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Readable")));
        mvc.perform(get("/api/v1/scenarios/" + id).session(viewer)).andExpect(status().isOk());
    }

    // --- Extra checks: empty fields, size limit ------------------------------------------------------

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void emptyNameIs400NamingTheField(String name) throws Exception {
        Map<String, Object> body = new HashMap<>(scenario("x"));
        body.put("name", name);

        create(signInAs(Role.DEVELOPER), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be blank"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " \n\t "})
    void emptyScriptIs400NamingTheField(String script) throws Exception {
        Map<String, Object> body = new HashMap<>(scenario("Empty script"));
        body.put("script", script);

        create(signInAs(Role.DEVELOPER), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("script"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be blank"));
        assertThat(scenarios.findByNameIgnoreCase("Empty script")).isEmpty();
    }

    @Test
    void tooLongNameOrDescriptionIs400() throws Exception {
        MockHttpSession developer = signInAs(Role.DEVELOPER);

        create(developer, scenario("n".repeat(201)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
        create(developer, Map.of("name", "Long description", "description", "d".repeat(2001), "script", SCRIPT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("description"));
    }

    @Test
    void scriptOfExactly1MbIsAccepted() throws Exception {
        String script = "// " + "x".repeat(MAX - 3);
        assertThat(ScriptHash.utf8Length(script)).isEqualTo(MAX);

        long id = idOf(create(signInAs(Role.DEVELOPER), Map.of("name", "Exactly 1 MB", "script", script))
                .andExpect(status().isCreated()));

        assertThat(versions.findByScenarioIdAndVersion(id, 1).orElseThrow().getScript()).isEqualTo(script);
    }

    @Test
    void oneByteMoreThan1MbIs400AndNothingIsSaved() throws Exception {
        String script = "// " + "x".repeat(MAX - 2);

        create(signInAs(Role.DEVELOPER), Map.of("name", "Too large", "script", script))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("script"))
                .andExpect(jsonPath("$.errors[0].message").value("must be at most 1 MB (1048576 bytes in UTF-8)"));
        assertThat(scenarios.findByNameIgnoreCase("Too large")).isEmpty();
    }

    @Test
    void theLimitCountsBytesNotCharacters() throws Exception {
        // Fewer than 1 048 576 characters, but each "ж" takes 2 bytes in UTF-8
        String script = "// " + "ж".repeat(MAX / 2);
        assertThat(script.length()).isLessThan(MAX);

        create(signInAs(Role.DEVELOPER), Map.of("name", "Cyrillic", "script", script))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("script"));
    }

    // --- Names, description, exact script ------------------------------------------------------------

    @Test
    void nameIsUniqueIgnoringCase() throws Exception {
        MockHttpSession developer = signInAs(Role.DEVELOPER);
        create(developer, scenario("Checkout API")).andExpect(status().isCreated());

        create(developer, scenario("  checkout api "))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A scenario named 'checkout api' already exists"));
    }

    @Test
    void nameAndDescriptionLoseSurroundingSpacesAndABlankDescriptionIsNone() throws Exception {
        MockHttpSession developer = signInAs(Role.DEVELOPER);

        create(developer, Map.of("name", "  Spaced  ", "description", "  Some text \n", "script", SCRIPT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Spaced"))
                .andExpect(jsonPath("$.description").value("Some text"));
        create(developer, Map.of("name", "No description", "description", "   ", "script", SCRIPT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").doesNotExist());
    }

    @Test
    void theScriptIsStoredExactlyAsSent() throws Exception {
        String script = "\n  // leading spaces, CRLF and Unicode: ✓ Привет\r\nexport default function () {}\r\n\n  ";

        long id = idOf(create(signInAs(Role.DEVELOPER), Map.of("name", "Exact", "script", script))
                .andExpect(status().isCreated()));

        ScenarioVersion v1 = versions.findByScenarioIdAndVersion(id, 1).orElseThrow();
        assertThat(v1.getScript()).isEqualTo(script);
        assertThat(v1.getSha256()).isEqualTo(ScriptHash.sha256Hex(script));
    }

    @Test
    void theListIsOrderedByNameIgnoringCase() throws Exception {
        MockHttpSession developer = signInAs(Role.DEVELOPER);
        for (String name : List.of("it-b", "IT-C", "it-a")) {
            create(developer, scenario(name)).andExpect(status().isCreated());
        }

        String body = mvc.perform(get("/api/v1/scenarios").session(developer))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<String> names = jsonMapper.readTree(body).findValuesAsString("name").stream()
                .filter(name -> name.toLowerCase().startsWith("it-"))
                .toList();
        assertThat(names).containsExactly("it-a", "it-b", "IT-C");
    }

    @Test
    void unknownScenarioIs404() throws Exception {
        mvc.perform(get("/api/v1/scenarios/999999999").session(signInAs(Role.VIEWER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No scenario with id 999999999"))
                .andExpect(jsonPath("$.detail", not(matchesPattern(".*Exception.*"))));
    }

    // --- Helpers -------------------------------------------------------------------------------------

    MockHttpSession signInAs(Role role) throws Exception {
        String login = TEST_LOGIN_PREFIX + role.name().toLowerCase() + "-" + System.nanoTime();
        userService.create(login, null, PASSWORD, role);
        return login(mvc, login, PASSWORD);
    }

    static Map<String, Object> scenario(String name) {
        return Map.of("name", name, "script", SCRIPT);
    }

    ResultActions create(MockHttpSession session, Map<String, Object> body) throws Exception {
        return mvc.perform(post("/api/v1/scenarios")
                .session(session)
                .with(browserCsrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(body)));
    }

    long idOf(ResultActions result) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(body).path("id").asLong();
    }
}
