package dev.ltp.worker.heartbeat;

import dev.ltp.common.protocol.WorkerProtocol.HeartbeatRequest;
import dev.ltp.common.protocol.WorkerProtocol.HeartbeatResponse;
import dev.ltp.common.protocol.WorkerProtocol.RegisterRequest;
import dev.ltp.common.protocol.WorkerProtocol.RegisterResponse;
import dev.ltp.common.protocol.WorkerProtocol.WorkerStatus;
import dev.ltp.worker.registration.ControlClient;
import dev.ltp.worker.registration.HostResources;
import dev.ltp.worker.registration.Registration;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

/**
 * The worker's side of US-06: register with the registration token, then send a heartbeat every 2 s.
 * Each {@link #step()} does one call and says how long to wait before the next one; {@link
 * WorkerAgentRunner} runs the steps in a loop.
 *
 * <ul>
 *   <li>Control Service not reachable: retry registering after 1, 2, 4, 5, 5, ... s, so a worker that
 *       started first is ONLINE within a few seconds of the Control Service.
 *   <li>Registration token refused (401): log an error, retry every 30 s.
 *   <li>Heartbeat refused (401/404: secret replaced, record gone): register again at once.
 *   <li>Other heartbeat errors: keep the registration and try again next interval; after 15 s without
 *       heartbeats the Control Service shows the worker LOST.
 * </ul>
 */
public class WorkerAgent {

    static final Duration WRONG_TOKEN_RETRY = Duration.ofSeconds(30);
    static final Duration MAX_RETRY = Duration.ofSeconds(5);

    private static final Logger log = LoggerFactory.getLogger(WorkerAgent.class);

    private final ControlClient control;
    private final String registrationToken;
    private final RegisterRequest identity;
    private final Duration heartbeatInterval;
    private final AtomicReference<Registration> registration = new AtomicReference<>();
    private int failedAttempts;

    public WorkerAgent(
            ControlClient control,
            String registrationToken,
            String name,
            String version,
            HostResources resources,
            Duration heartbeatInterval) {
        this.control = control;
        this.registrationToken = registrationToken;
        this.identity = new RegisterRequest(name, version, resources.cpus(), resources.memMb());
        this.heartbeatInterval = heartbeatInterval;
    }

    /** One call to the Control Service; returns the pause before the next step. */
    public Duration step() {
        Registration current = registration.get();
        return current == null ? register() : heartbeat(current);
    }

    /** Tells the Control Service that this worker stops (OFFLINE). Never throws. */
    public void deregister() {
        Registration current = registration.getAndSet(null);
        if (current == null) {
            return;
        }
        try {
            control.deregister(current);
            log.info("Deregistered worker {} from the Control Service", current.workerId());
        } catch (RestClientException e) {
            log.warn("Could not deregister worker {}: {}", current.workerId(), e.getMessage());
        }
    }

    public Optional<Registration> registration() {
        return Optional.ofNullable(registration.get());
    }

    private Duration register() {
        try {
            RegisterResponse response = control.register(identity, registrationToken);
            registration.set(new Registration(response.workerId(), response.workerSecret()));
            failedAttempts = 0;
            log.info(
                    "Registered as worker {} ('{}', {} CPUs, {} MB)",
                    response.workerId(),
                    identity.name(),
                    identity.cpus(),
                    identity.memMb());
            return heartbeatInterval;
        } catch (HttpClientErrorException.Unauthorized e) {
            log.error(
                    "The Control Service refused the registration token. Set LTP_WORKER_REGISTRATION_TOKEN to the "
                            + "value the Control Service uses. Trying again in {} s.",
                    WRONG_TOKEN_RETRY.toSeconds());
            return WRONG_TOKEN_RETRY;
        } catch (RestClientException e) {
            Duration retry = nextRetry();
            log.warn("Cannot register with the Control Service ({}). Trying again in {} s.", e.getMessage(), retry.toSeconds());
            return retry;
        }
    }

    private Duration heartbeat(Registration current) {
        try {
            HeartbeatResponse response = control.heartbeat(current, new HeartbeatRequest(WorkerStatus.ONLINE, null));
            if (!response.commands().isEmpty()) {
                // ASSIGN and CANCEL are handled from Sprint 2 (US-03, US-05)
                log.warn("Ignoring {} command(s) from the Control Service", response.commands().size());
            }
            return heartbeatInterval;
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.NotFound e) {
            log.warn("The Control Service no longer accepts worker {} ({}). Registering again.", current.workerId(), e.getStatusCode());
            registration.compareAndSet(current, null);
            return Duration.ZERO;
        } catch (RestClientException e) {
            log.warn("Heartbeat of worker {} failed: {}", current.workerId(), e.getMessage());
            return heartbeatInterval;
        }
    }

    private Duration nextRetry() {
        long seconds = 1L << Math.min(failedAttempts, 10);
        failedAttempts++;
        return Duration.ofSeconds(Math.min(seconds, MAX_RETRY.toSeconds()));
    }
}
