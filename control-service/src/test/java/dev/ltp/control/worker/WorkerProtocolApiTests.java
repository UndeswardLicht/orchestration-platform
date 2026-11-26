package dev.ltp.control.worker;

import static dev.ltp.control.BrowserCsrf.browserCsrf;
import static dev.ltp.control.auth.AuthTestSupport.PASSWORD;
import static dev.ltp.control.auth.AuthTestSupport.TEST_LOGIN_PREFIX;
import static dev.ltp.control.auth.AuthTestSupport.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ltp.common.protocol.WorkerProtocol;
import dev.ltp.control.IntegrationTest;
import dev.ltp.control.auth.Role;
import dev.ltp.control.auth.UserService;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * US-06: workers register with the registration token, send heartbeats, and show their status. Rolled
 * back after every test. LOST is tested by calling {@link HeartbeatMonitor#markLost} with a later time,
 * so no test waits 15 s.
 */
@IntegrationTest
@Transactional
class WorkerProtocolApiTests {

    static final String REGISTER = "/internal/v1/workers/register";

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper jsonMapper;

    @Autowired
    WorkerProperties properties;

    @Autowired
    WorkerRepository workers;

    @Autowired
    HeartbeatMonitor monitor;

    @Autowired
    UserService userService;

    // --- Acceptance criterion ------------------------------------------------------------------------

    @Test
    void workerWithValidTokenIsOnlineAtOnceAndItsHeartbeatTimeIsUpdated() throws Exception {
        String name = uniqueName();
        Registered worker = register(name);

        JsonNode listed = listedWorker(name);
        assertThat(listed.path("status").asString()).isEqualTo("ONLINE");
        assertThat(listed.path("id").asLong()).isEqualTo(worker.id());
        Instant registeredHeartbeat = Instant.parse(listed.path("lastHeartbeatAt").asString());

        Thread.sleep(5);
        heartbeat(worker, "ONLINE").andExpect(status().isOk()).andExpect(jsonPath("$.commands").isEmpty());

        JsonNode after = listedWorker(name);
        assertThat(after.path("status").asString()).isEqualTo("ONLINE");
        assertThat(Instant.parse(after.path("lastHeartbeatAt").asString())).isAfter(registeredHeartbeat);
        assertThat(after.path("cpus").asDouble()).isEqualTo(2.0);
        assertThat(after.path("memMb").asInt()).isEqualTo(4096);
        assertThat(after.path("version").asString()).isEqualTo("0.1.0");
    }

    // --- Extra checks of the story -------------------------------------------------------------------

    @Test
    void wrongRegistrationTokenIs401AndCreatesNoWorker() throws Exception {
        String name = uniqueName();
        mvc.perform(post(REGISTER)
                        .header(WorkerProtocol.REGISTRATION_TOKEN_HEADER, properties.registrationToken() + "-wrong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(name)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(Matchers.containsString("registration token")));

        assertThat(workers.findByName(name)).isEmpty();
    }

    @Test
    void missingRegistrationTokenIs401AndCreatesNoWorker() throws Exception {
        String name = uniqueName();
        mvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON).content(registerBody(name)))
                .andExpect(status().isUnauthorized());

        assertThat(workers.findByName(name)).isEmpty();
    }

    @Test
    void registeringTheSameNameAgainReusesTheRecordAndReplacesTheSecret() throws Exception {
        String name = uniqueName();
        Registered first = register(name);
        monitor.markLost(Instant.now().plus(Duration.ofMinutes(1)));

        Registered again = register(name);

        assertThat(again.id()).isEqualTo(first.id());
        assertThat(again.secret()).isNotEqualTo(first.secret());
        assertThat(workers.findAll().stream().filter(w -> w.getName().equals(name))).hasSize(1);
        assertThat(listedWorker(name).path("status").asString()).isEqualTo("ONLINE");
        heartbeat(first, "ONLINE").andExpect(status().isUnauthorized());
        heartbeat(again, "ONLINE").andExpect(status().isOk());
    }

    @Test
    void registeringAgainWhileStillOnlineTakesTheRecordOver() throws Exception {
        String name = uniqueName();
        Registered first = register(name);

        Registered again = register(name);

        assertThat(again.id()).isEqualTo(first.id());
        heartbeat(first, "ONLINE").andExpect(status().isUnauthorized());
        heartbeat(again, "ONLINE").andExpect(status().isOk());
    }

    @Test
    void reRegistrationUpdatesWhatTheWorkerReportsAndKeepsTheFirstRegistrationTime() throws Exception {
        String name = uniqueName();
        register(name);
        String registeredAt = listedWorker(name).path("registeredAt").asString();

        registerWith(Map.of("name", name, "version", "0.2.0", "cpus", 4, "memMb", 8192))
                .andExpect(status().isOk());

        JsonNode listed = listedWorker(name);
        assertThat(listed.path("version").asString()).isEqualTo("0.2.0");
        assertThat(listed.path("cpus").asDouble()).isEqualTo(4.0);
        assertThat(listed.path("memMb").asInt()).isEqualTo(8192);
        assertThat(listed.path("registeredAt").asString()).isEqualTo(registeredAt);
    }

    // --- LOST, OFFLINE and coming back ---------------------------------------------------------------

    @Test
    void workerWithoutHeartbeatForMoreThan15SecondsIsLost() throws Exception {
        String name = uniqueName();
        register(name);
        Instant lastHeartbeat = Instant.parse(listedWorker(name).path("lastHeartbeatAt").asString());

        monitor.markLost(lastHeartbeat.plus(Duration.ofSeconds(14)));
        assertThat(listedWorker(name).path("status").asString()).isEqualTo("ONLINE");

        monitor.markLost(lastHeartbeat.plus(Duration.ofSeconds(16)));
        assertThat(listedWorker(name).path("status").asString()).isEqualTo("LOST");
    }

    @Test
    void lostWorkerThatSendsAHeartbeatAgainIsOnline() throws Exception {
        String name = uniqueName();
        Registered worker = register(name);
        monitor.markLost(Instant.now().plus(Duration.ofMinutes(1)));
        assertThat(listedWorker(name).path("status").asString()).isEqualTo("LOST");

        heartbeat(worker, "ONLINE").andExpect(status().isOk());

        assertThat(listedWorker(name).path("status").asString()).isEqualTo("ONLINE");
    }

    @Test
    void busyIsShownAsReported() throws Exception {
        String name = uniqueName();
        Registered worker = register(name);

        heartbeat(worker, "BUSY").andExpect(status().isOk());

        assertThat(listedWorker(name).path("status").asString()).isEqualTo("BUSY");
    }

    @Test
    void cleanShutdownShowsOfflineAtOnceAndIsNeverMarkedLost() throws Exception {
        String name = uniqueName();
        Registered worker = register(name);

        mvc.perform(post("/internal/v1/workers/" + worker.id() + "/deregister").header(HttpHeaders.AUTHORIZATION, bearer(worker)))
                .andExpect(status().isNoContent());
        assertThat(listedWorker(name).path("status").asString()).isEqualTo("OFFLINE");

        monitor.markLost(Instant.now().plus(Duration.ofMinutes(1)));
        assertThat(listedWorker(name).path("status").asString()).isEqualTo("OFFLINE");

        heartbeat(worker, "ONLINE").andExpect(status().isOk());
        assertThat(listedWorker(name).path("status").asString()).isEqualTo("ONLINE");
    }

    // --- Worker credentials --------------------------------------------------------------------------

    @Test
    void heartbeatWithoutOrWithAWrongSecretIs401() throws Exception {
        Registered worker = register(uniqueName());

        mvc.perform(post(heartbeatUrl(worker)).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ONLINE\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Missing, unknown or replaced worker secret. Register again."));
        mvc.perform(post(heartbeatUrl(worker))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-the-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ONLINE\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aWorkerCannotSendHeartbeatsForAnotherWorker() throws Exception {
        Registered one = register(uniqueName());
        Registered other = register(uniqueName());

        mvc.perform(post(heartbeatUrl(other))
                        .header(HttpHeaders.AUTHORIZATION, bearer(one))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ONLINE\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/internal/v1/workers/" + other.id() + "/deregister").header(HttpHeaders.AUTHORIZATION, bearer(one)))
                .andExpect(status().isForbidden());
    }

    @Test
    void theRegistrationTokenIsNoWorkerSecret() throws Exception {
        Registered worker = register(uniqueName());

        mvc.perform(post(heartbeatUrl(worker))
                        .header(WorkerProtocol.REGISTRATION_TOKEN_HEADER, properties.registrationToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ONLINE\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aSignedInBrowserSessionDoesNotCountOnTheWorkerProtocol() throws Exception {
        Registered worker = register(uniqueName());
        MockHttpSession admin = login(mvc, IntegrationTest.ADMIN_LOGIN, IntegrationTest.ADMIN_PASSWORD);

        mvc.perform(post(heartbeatUrl(worker))
                        .session(admin)
                        .with(browserCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ONLINE\"}"))
                .andExpect(status().isUnauthorized());
    }

    // --- Validation ----------------------------------------------------------------------------------

    @Test
    void invalidRegistrationsAre400() throws Exception {
        registerWith(Map.of("name", uniqueName(), "version", "0.1.0", "cpus", 0, "memMb", 4096))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("cpus"));
        registerWith(Map.of("name", "worker one", "version", "0.1.0", "cpus", 2, "memMb", 4096))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
        registerWith(Map.of("name", uniqueName(), "version", "", "cpus", 2, "memMb", 4096))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("version"));
        registerWith(Map.of("name", uniqueName(), "version", "0.1.0", "cpus", 2))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("memMb"));
    }

    @Test
    void aWorkerCannotReportItselfLostOrOffline() throws Exception {
        Registered worker = register(uniqueName());

        heartbeat(worker, "LOST").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("status"));
        heartbeat(worker, "OFFLINE").andExpect(status().isBadRequest());
    }

    // --- The list for the Workers page ---------------------------------------------------------------

    @Test
    void everySignedInRoleSeesTheWorkersOrderedByName() throws Exception {
        String prefix = uniqueName();
        register(prefix + "-b");
        register(prefix + "-a");
        String viewer = TEST_LOGIN_PREFIX + "viewer-" + System.nanoTime();
        userService.create(viewer, null, PASSWORD, Role.VIEWER);

        String body = mvc.perform(get("/api/v1/workers").session(login(mvc, viewer, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(jsonMapper.readTree(body).valueStream()
                        .map(worker -> worker.path("name").asString())
                        .filter(name -> name.startsWith(prefix)))
                .containsExactly(prefix + "-a", prefix + "-b");
    }

    @Test
    void theListNeverShowsSecrets() throws Exception {
        String name = uniqueName();
        Registered worker = register(name);

        JsonNode listed = listedWorker(name);
        assertThat(listed.has("secretHash")).isFalse();
        assertThat(listed.toString()).doesNotContain(worker.secret()).doesNotContain(WorkerSecrets.hash(worker.secret()));
    }

    // --- Helpers -------------------------------------------------------------------------------------

    record Registered(long id, String secret) {}

    static String uniqueName() {
        return "it-worker-" + System.nanoTime();
    }

    Registered register(String name) throws Exception {
        String body = mvc.perform(post(REGISTER)
                        .header(WorkerProtocol.REGISTRATION_TOKEN_HEADER, properties.registrationToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(name)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode json = jsonMapper.readTree(body);
        return new Registered(json.path("workerId").asLong(), json.path("workerSecret").asString());
    }

    ResultActions registerWith(Map<String, Object> body) throws Exception {
        return mvc.perform(post(REGISTER)
                .header(WorkerProtocol.REGISTRATION_TOKEN_HEADER, properties.registrationToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(body)));
    }

    String registerBody(String name) {
        return jsonMapper.writeValueAsString(Map.of("name", name, "version", "0.1.0", "cpus", 2, "memMb", 4096));
    }

    ResultActions heartbeat(Registered worker, String status) throws Exception {
        return mvc.perform(post(heartbeatUrl(worker))
                .header(HttpHeaders.AUTHORIZATION, bearer(worker))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\"}"));
    }

    static String heartbeatUrl(Registered worker) {
        return "/internal/v1/workers/" + worker.id() + "/heartbeat";
    }

    static String bearer(Registered worker) {
        return "Bearer " + worker.secret();
    }

    JsonNode listedWorker(String name) throws Exception {
        MockHttpSession admin = login(mvc, IntegrationTest.ADMIN_LOGIN, IntegrationTest.ADMIN_PASSWORD);
        String body = mvc.perform(get("/api/v1/workers").session(admin))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return jsonMapper.readTree(body).valueStream()
                .filter(worker -> worker.path("name").asString().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("worker " + name + " not listed"));
    }
}
