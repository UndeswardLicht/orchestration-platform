package dev.ltp.worker.heartbeat;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/** Actuator health is DOWN until the worker is registered with the Control Service (US-06). */
public class RegistrationHealthIndicator implements HealthIndicator {

    private final WorkerAgent agent;

    public RegistrationHealthIndicator(WorkerAgent agent) {
        this.agent = agent;
    }

    @Override
    public Health health() {
        return agent.registration()
                .map(registration -> Health.up().withDetail("workerId", registration.workerId()).build())
                .orElseGet(() -> Health.down().withDetail("reason", "not registered with the Control Service").build());
    }
}
