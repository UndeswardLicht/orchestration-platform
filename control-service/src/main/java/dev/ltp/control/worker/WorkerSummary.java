package dev.ltp.control.worker;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.Instant;

/** A registered worker as the Workers page shows it (US-06). */
@Schema(description = "A registered worker and its status")
public record WorkerSummary(
        @Schema(example = "3", requiredMode = RequiredMode.REQUIRED) long id,
        @Schema(example = "worker-1", requiredMode = RequiredMode.REQUIRED) String name,
        @Schema(
                        example = "ONLINE",
                        requiredMode = RequiredMode.REQUIRED,
                        description = "ONLINE: free; BUSY: runs a segment; LOST: no heartbeat for more than 15 s; "
                                + "OFFLINE: stopped cleanly")
                WorkerStatus status,
        @Schema(example = "2", requiredMode = RequiredMode.REQUIRED, description = "CPUs the worker can use")
                double cpus,
        @Schema(example = "4096", requiredMode = RequiredMode.REQUIRED, description = "Memory the worker can use, MB")
                int memMb,
        @Schema(example = "0.1.0", requiredMode = RequiredMode.REQUIRED, description = "Worker software version")
                String version,
        @Schema(example = "2026-11-27T10:15:02Z", requiredMode = RequiredMode.REQUIRED) Instant lastHeartbeatAt,
        @Schema(example = "2026-11-27T08:00:00Z", requiredMode = RequiredMode.REQUIRED, description = "First registration")
                Instant registeredAt) {

    static WorkerSummary of(Worker worker) {
        return new WorkerSummary(
                worker.getId(),
                worker.getName(),
                worker.getStatus(),
                worker.getCpus(),
                worker.getMemMb(),
                worker.getVersion(),
                worker.getLastHeartbeatAt(),
                worker.getRegisteredAt());
    }
}
