package dev.ltp.control.worker;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Registered workers and their status, for every signed-in role (US-06). */
@RestController
@RequestMapping("/api/v1/workers")
@Tag(name = "Workers", description = "Load generators and their status")
class WorkerController {

    private final WorkerService workerService;

    WorkerController(WorkerService workerService) {
        this.workerService = workerService;
    }

    @GetMapping
    @Operation(
            summary = "List workers",
            description = "Every registered worker with its status and last heartbeat, ordered by name. "
                    + "Statuses: ONLINE (free), BUSY (runs a segment), LOST (no heartbeat for more than 15 s), "
                    + "OFFLINE (stopped cleanly).")
    @ApiResponse(
            responseCode = "200",
            description = "Workers",
            content = @Content(
                    array = @ArraySchema(schema = @Schema(implementation = WorkerSummary.class)),
                    examples = @ExampleObject(value = """
                            [{"id": 3, "name": "worker-1", "status": "ONLINE", "cpus": 2, "memMb": 4096,
                              "version": "0.1.0", "lastHeartbeatAt": "2026-11-27T10:15:02Z",
                              "registeredAt": "2026-11-27T08:00:00Z"},
                             {"id": 4, "name": "worker-2", "status": "LOST", "cpus": 2, "memMb": 4096,
                              "version": "0.1.0", "lastHeartbeatAt": "2026-11-27T10:12:40Z",
                              "registeredAt": "2026-11-27T08:00:01Z"}]""")))
    List<WorkerSummary> list() {
        return workerService.list();
    }
}
