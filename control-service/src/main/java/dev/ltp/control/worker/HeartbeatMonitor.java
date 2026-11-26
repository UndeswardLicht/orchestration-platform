package dev.ltp.control.worker;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marks workers LOST that sent no heartbeat for longer than {@code ltp.worker.heartbeat-timeout}
 * (US-06; NFR-04: timeout 15 s, checked every second, so a lost worker shows within about 16 s of its
 * last heartbeat). Reissuing a lost worker's segment follows in US-07.
 */
@Component
public class HeartbeatMonitor {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatMonitor.class);

    private final WorkerRepository workers;
    private final WorkerProperties properties;
    private final Clock clock;

    HeartbeatMonitor(WorkerRepository workers, WorkerProperties properties, Clock clock) {
        this.workers = workers;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 1000)
    void tick() {
        markLost(clock.instant());
    }

    /** Marks every worker LOST whose last heartbeat is older than the timeout at {@code now}. */
    @Transactional
    public int markLost(Instant now) {
        Instant cutoff = now.minus(properties.heartbeatTimeout());
        List<Worker> silent = workers.findSilentSince(cutoff);
        if (silent.isEmpty()) {
            return 0;
        }
        int marked = workers.markLostSilentSince(cutoff);
        silent.forEach(worker -> log.warn(
                "Worker '{}' (id {}) is LOST: no heartbeat since {}",
                worker.getName(),
                worker.getId(),
                worker.getLastHeartbeatAt()));
        return marked;
    }
}
