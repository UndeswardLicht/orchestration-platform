package dev.ltp.worker.heartbeat;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * Runs the {@link WorkerAgent} on its own (virtual) thread from start-up to shutdown. On a clean shutdown
 * (SIGTERM, {@code docker compose stop}) it deregisters, so the worker shows OFFLINE at once.
 */
public class WorkerAgentRunner implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(WorkerAgentRunner.class);

    private final WorkerAgent agent;
    private volatile Thread thread;

    public WorkerAgentRunner(WorkerAgent agent) {
        this.agent = agent;
    }

    @Override
    public synchronized void start() {
        if (thread != null) {
            return;
        }
        thread = Thread.ofVirtual().name("worker-agent").start(this::loop);
    }

    @Override
    public synchronized void stop() {
        Thread running = thread;
        thread = null;
        if (running != null) {
            running.interrupt();
            try {
                running.join(Duration.ofSeconds(5));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        agent.deregister();
    }

    @Override
    public boolean isRunning() {
        return thread != null;
    }

    private void loop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Duration pause = agent.step();
                if (pause.isPositive()) {
                    Thread.sleep(pause);
                }
            } catch (InterruptedException e) {
                return;
            } catch (RuntimeException e) {
                // A bug in one step must not stop the heartbeats for good
                log.error("Unexpected error in the worker agent", e);
                try {
                    Thread.sleep(Duration.ofSeconds(1));
                } catch (InterruptedException interrupted) {
                    return;
                }
            }
        }
    }
}
