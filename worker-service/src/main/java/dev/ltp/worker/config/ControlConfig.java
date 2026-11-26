package dev.ltp.worker.config;

import dev.ltp.worker.heartbeat.RegistrationHealthIndicator;
import dev.ltp.worker.heartbeat.WorkerAgent;
import dev.ltp.worker.heartbeat.WorkerAgentRunner;
import dev.ltp.worker.registration.ControlClient;
import dev.ltp.worker.registration.HostResources;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Connection to the Control Service (US-06): HTTP client with short timeouts, the agent that registers
 * and sends heartbeats, and its health. {@code ltp.worker.agent.enabled=false} keeps the agent from
 * starting (tests that only need the context).
 */
@Configuration(proxyBeanMethods = false)
class ControlConfig {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);

    /** Shorter than the heartbeat timeout of the Control Service (15 s), so a hung call cannot hide. */
    static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    ControlClient controlClient(RestClient.Builder builder, WorkerProperties properties) {
        // HttpURLConnection: one call at a time is all a worker needs, and it opens nothing until used
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return new ControlClient(builder.clone()
                .baseUrl(properties.controlUrl().toString())
                .requestFactory(requestFactory)
                .build());
    }

    @Bean
    WorkerAgent workerAgent(ControlClient controlClient, WorkerProperties properties) {
        return new WorkerAgent(
                controlClient,
                properties.registrationToken(),
                properties.name(),
                properties.version(),
                HostResources.detect(),
                properties.heartbeatInterval());
    }

    @Bean
    @ConditionalOnBooleanProperty(name = "ltp.worker.agent.enabled", matchIfMissing = true)
    WorkerAgentRunner workerAgentRunner(WorkerAgent workerAgent) {
        return new WorkerAgentRunner(workerAgent);
    }

    @Bean
    RegistrationHealthIndicator registrationHealthIndicator(WorkerAgent workerAgent) {
        return new RegistrationHealthIndicator(workerAgent);
    }
}
