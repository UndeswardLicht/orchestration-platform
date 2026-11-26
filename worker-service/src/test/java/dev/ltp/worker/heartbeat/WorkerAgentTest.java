package dev.ltp.worker.heartbeat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.client.ExpectedCount.between;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dev.ltp.common.protocol.WorkerProtocol;
import dev.ltp.worker.registration.ControlClient;
import dev.ltp.worker.registration.HostResources;
import dev.ltp.worker.registration.Registration;
import java.io.IOException;
import java.net.ConnectException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestClient;

/** US-06 on the worker side, against a fake Control Service. */
class WorkerAgentTest {

    static final String CONTROL = "http://control:8080";
    static final String TOKEN = "registration-token";
    static final Duration INTERVAL = Duration.ofSeconds(2);
    static final String REGISTERED = """
            {"workerId": 3, "workerSecret": "secret-3"}""";

    MockRestServiceServer control;
    WorkerAgent agent;

    @BeforeEach
    void fakeControlService() {
        RestClient.Builder builder = RestClient.builder().baseUrl(CONTROL);
        control = MockRestServiceServer.bindTo(builder).build();
        agent = new WorkerAgent(
                new ControlClient(builder.build()), TOKEN, "worker-1", "0.1.0", new HostResources(2, 4096), INTERVAL);
    }

    @Test
    void registersWithTheTokenAndThenSendsHeartbeatsWithItsSecret() {
        expectRegistration().andRespond(withSuccess(REGISTERED, MediaType.APPLICATION_JSON));
        control.expect(manyTimes(), requestTo(CONTROL + "/internal/v1/workers/3/heartbeat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer secret-3"))
                .andExpect(content().json("{\"status\": \"ONLINE\"}"))
                .andRespond(withSuccess("{\"commands\": []}", MediaType.APPLICATION_JSON));

        assertThat(agent.step()).isEqualTo(INTERVAL);
        assertThat(agent.registration()).contains(new Registration(3, "secret-3"));
        assertThat(agent.step()).isEqualTo(INTERVAL);
        assertThat(agent.step()).isEqualTo(INTERVAL);

        control.verify();
    }

    @Test
    void aRefusedRegistrationTokenIsRetriedEvery30Seconds() {
        expectRegistration().andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThat(agent.step()).isEqualTo(Duration.ofSeconds(30));
        assertThat(agent.registration()).isEmpty();
    }

    @Test
    void whileTheControlServiceIsDownRegistrationIsRetriedSoonAndOften() {
        control.expect(manyTimes(), requestTo(CONTROL + "/internal/v1/workers/register"))
                .andRespond(withException(new ConnectException("Connection refused")));

        List<Long> retries = new ArrayList<>();
        for (int attempt = 0; attempt < 6; attempt++) {
            retries.add(agent.step().toSeconds());
        }

        // Never more than 5 s apart, so the worker is ONLINE within seconds once the Control Service is up
        assertThat(retries).containsExactly(1L, 2L, 4L, 5L, 5L, 5L);
    }

    @Test
    void registersAgainAtOnceWhenTheControlServiceNoLongerAcceptsTheSecret() {
        expectRegistration().andRespond(withSuccess(REGISTERED, MediaType.APPLICATION_JSON));
        control.expect(once(), requestTo(CONTROL + "/internal/v1/workers/3/heartbeat"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        expectRegistration().andRespond(withSuccess("""
                {"workerId": 3, "workerSecret": "secret-new"}""", MediaType.APPLICATION_JSON));

        agent.step();
        assertThat(agent.step()).isEqualTo(Duration.ZERO);
        assertThat(agent.registration()).isEmpty();
        agent.step();

        assertThat(agent.registration()).contains(new Registration(3, "secret-new"));
        control.verify();
    }

    @Test
    void keepsItsRegistrationWhenAHeartbeatFailsForAnotherReason() {
        expectRegistration().andRespond(withSuccess(REGISTERED, MediaType.APPLICATION_JSON));
        control.expect(once(), requestTo(CONTROL + "/internal/v1/workers/3/heartbeat"))
                .andRespond(withException(new IOException("timeout")));
        control.expect(once(), requestTo(CONTROL + "/internal/v1/workers/3/heartbeat"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        agent.step();
        assertThat(agent.step()).isEqualTo(INTERVAL);
        assertThat(agent.step()).isEqualTo(INTERVAL);

        assertThat(agent.registration()).isPresent();
    }

    @Test
    void deregistersOnStopSoTheWorkerShowsOffline() {
        expectRegistration().andRespond(withSuccess(REGISTERED, MediaType.APPLICATION_JSON));
        control.expect(once(), requestTo(CONTROL + "/internal/v1/workers/3/deregister"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer secret-3"))
                .andRespond(withNoContent());

        agent.step();
        agent.deregister();

        assertThat(agent.registration()).isEmpty();
        control.verify();
    }

    @Test
    void aWorkerThatNeverRegisteredDoesNotDeregister() {
        agent.deregister();

        control.verify();
    }

    @Test
    void aFailedDeregistrationDoesNotStopTheShutdown() {
        expectRegistration().andRespond(withSuccess(REGISTERED, MediaType.APPLICATION_JSON));
        control.expect(once(), requestTo(CONTROL + "/internal/v1/workers/3/deregister"))
                .andRespond(withException(new ConnectException("Connection refused")));

        agent.step();
        agent.deregister();

        assertThat(agent.registration()).isEmpty();
    }

    // Story acceptance criterion, worker side: a started worker registers (is ONLINE) within 10 s
    @Test
    void theRunnerRegistersRightAfterStartAndDeregistersOnStop() {
        RestClient.Builder builder = RestClient.builder().baseUrl(CONTROL);
        // Heartbeats may or may not come before the stop, so the order is not checked
        control = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        agent = new WorkerAgent(
                new ControlClient(builder.build()), TOKEN, "worker-1", "0.1.0", new HostResources(2, 4096), INTERVAL);
        expectRegistration().andRespond(withSuccess(REGISTERED, MediaType.APPLICATION_JSON));
        control.expect(between(0, 10), requestTo(CONTROL + "/internal/v1/workers/3/heartbeat"))
                .andRespond(withSuccess("{\"commands\": []}", MediaType.APPLICATION_JSON));
        control.expect(once(), requestTo(CONTROL + "/internal/v1/workers/3/deregister"))
                .andRespond(withNoContent());
        WorkerAgentRunner runner = new WorkerAgentRunner(agent);
        RegistrationHealthIndicator health = new RegistrationHealthIndicator(agent);
        assertThat(health.health().getStatus().getCode()).isEqualTo("DOWN");

        runner.start();
        try {
            await().atMost(Duration.ofSeconds(10)).until(() -> agent.registration().isPresent());
            assertThat(health.health().getStatus().getCode()).isEqualTo("UP");
        } finally {
            runner.stop();
        }

        assertThat(runner.isRunning()).isFalse();
        assertThat(agent.registration()).isEmpty();
        control.verify();
    }

    ResponseActions expectRegistration() {
        return control.expect(once(), requestTo(CONTROL + "/internal/v1/workers/register"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(WorkerProtocol.REGISTRATION_TOKEN_HEADER, TOKEN))
                .andExpect(content().json("""
                        {"name": "worker-1", "version": "0.1.0", "cpus": 2, "memMb": 4096}"""));
    }
}
