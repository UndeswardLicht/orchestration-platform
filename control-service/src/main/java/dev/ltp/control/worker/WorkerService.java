package dev.ltp.control.worker;

import dev.ltp.common.protocol.WorkerProtocol.RegisterResponse;
import dev.ltp.control.config.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration, heartbeats and clean shutdown of workers (US-06). Statuses: registration and every
 * heartbeat make a worker ONLINE (or BUSY, as it reports); {@link HeartbeatMonitor} makes a silent one
 * LOST; a clean shutdown makes it OFFLINE.
 */
@Service
public class WorkerService {

    private static final Logger log = LoggerFactory.getLogger(WorkerService.class);

    private final WorkerRepository workers;
    private final WorkerProperties properties;
    private final Clock clock;

    WorkerService(WorkerRepository workers, WorkerProperties properties, Clock clock) {
        this.workers = workers;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Registers a worker. A known name reuses its record (a restarted worker) and gets a new secret; the
     * old secret stops working. If that record still looked alive, two workers may share the name, which
     * is logged.
     */
    @Transactional
    public RegisterResponse register(String name, int cpus, int memMb, String version) {
        Instant now = now();
        String secret = WorkerSecrets.generate();
        String secretHash = WorkerSecrets.hash(secret);
        Worker worker = workers.findByName(name).orElse(null);
        if (worker == null) {
            try {
                worker = workers.saveAndFlush(new Worker(name, cpus, memMb, version, secretHash, now));
            } catch (DataIntegrityViolationException e) {
                throw ApiException.conflict("Worker '" + name + "' is registering at the same time. Try again.");
            }
            log.info("Worker '{}' registered as id {} ({} CPUs, {} MB, version {})", name, worker.getId(), cpus, memMb, version);
        } else {
            if (looksAlive(worker, now)) {
                log.warn("Worker '{}' registered again while its record was still {}: a restart, or two workers "
                        + "share this name (they would keep replacing each other)", name, worker.getStatus());
            }
            worker.reRegister(cpus, memMb, version, secretHash, now);
            log.info("Worker '{}' (id {}) registered again ({} CPUs, {} MB, version {})", name, worker.getId(), cpus, memMb, version);
        }
        return new RegisterResponse(worker.getId(), secret);
    }

    /** Records a heartbeat of the authenticated worker, which may only report for itself. */
    @Transactional
    public void heartbeat(WorkerPrincipal principal, long workerId, WorkerStatus reported) {
        Worker worker = own(principal, workerId);
        if (reported != WorkerStatus.ONLINE && reported != WorkerStatus.BUSY) {
            throw ApiException.badRequest("status", "must be ONLINE or BUSY");
        }
        WorkerStatus before = worker.getStatus();
        worker.heartbeat(reported, now());
        if (before == WorkerStatus.LOST || before == WorkerStatus.OFFLINE) {
            log.info("Worker '{}' (id {}) is back: {} -> {}", worker.getName(), worker.getId(), before, reported);
        }
    }

    /** The worker shuts down cleanly and shows OFFLINE at once instead of LOST after the timeout. */
    @Transactional
    public void deregister(WorkerPrincipal principal, long workerId) {
        Worker worker = own(principal, workerId);
        worker.goOffline();
        log.info("Worker '{}' (id {}) stopped cleanly: OFFLINE", worker.getName(), worker.getId());
    }

    @Transactional(readOnly = true)
    public List<WorkerSummary> list() {
        return workers.findAllByOrderByNameAsc().stream().map(WorkerSummary::of).toList();
    }

    private Worker own(WorkerPrincipal principal, long workerId) {
        if (principal.workerId() != workerId) {
            throw ApiException.forbidden("This secret belongs to worker " + principal.workerId() + ", not " + workerId);
        }
        // The secret was just found in the table, so the row exists unless it was deleted meanwhile
        return workers.findById(workerId).orElseThrow(() -> ApiException.notFound("No worker with id " + workerId));
    }

    private boolean looksAlive(Worker worker, Instant now) {
        boolean running = worker.getStatus() == WorkerStatus.ONLINE || worker.getStatus() == WorkerStatus.BUSY;
        return running && worker.getLastHeartbeatAt().isAfter(now.minus(properties.heartbeatTimeout()));
    }

    /** PostgreSQL keeps microseconds; truncating keeps saved and returned times equal. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
