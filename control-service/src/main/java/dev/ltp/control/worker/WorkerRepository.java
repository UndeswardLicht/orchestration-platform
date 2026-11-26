package dev.ltp.control.worker;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface WorkerRepository extends JpaRepository<Worker, Long> {

    Optional<Worker> findByName(String name);

    List<Worker> findByStatus(WorkerStatus status);

    Optional<Worker> findBySecretHash(String secretHash);

    List<Worker> findAllByOrderByNameAsc();

    /** Workers that count as running (ONLINE or BUSY) but sent no heartbeat since {@code cutoff}. */
    @Query("select w from Worker w where w.status in (dev.ltp.control.worker.WorkerStatus.ONLINE, "
            + "dev.ltp.control.worker.WorkerStatus.BUSY) and w.lastHeartbeatAt < :cutoff")
    List<Worker> findSilentSince(Instant cutoff);

    /**
     * Marks the silent workers LOST in one statement. The condition is checked again in the update, so a
     * heartbeat that arrives meanwhile is never overwritten.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Worker w set w.status = dev.ltp.control.worker.WorkerStatus.LOST "
            + "where w.status in (dev.ltp.control.worker.WorkerStatus.ONLINE, "
            + "dev.ltp.control.worker.WorkerStatus.BUSY) and w.lastHeartbeatAt < :cutoff")
    int markLostSilentSince(Instant cutoff);
}
