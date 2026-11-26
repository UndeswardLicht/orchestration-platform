package dev.ltp.control.worker;

import dev.ltp.common.protocol.WorkerProtocol;
import dev.ltp.control.config.OpenApiConfig;
import dev.ltp.common.protocol.WorkerProtocol.HeartbeatResponse;
import dev.ltp.common.protocol.WorkerProtocol.RegisterResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The worker side of the pull protocol (US-06, 05-tech-stack.md §4). Workers always open the connection,
 * so they need no inbound ports. Not public: Caddy answers 404 for {@code /internal/**}; workers reach
 * {@code control:8080} on the internal network.
 */
@RestController
@RequestMapping(WorkerProtocol.BASE_PATH + "/workers")
@Tag(
        name = "Worker protocol (internal)",
        description = "Called by workers only, on the internal network. Not reachable through the public "
                + "address (the reverse proxy answers 404).")
class WorkerProtocolController {

    static final String REGISTRATION_TOKEN = OpenApiConfig.REGISTRATION_TOKEN_SCHEME;
    static final String WORKER_SECRET = OpenApiConfig.WORKER_SECRET_SCHEME;

    private static final String BAD_TOKEN_EXAMPLE = """
            {"type": "about:blank", "title": "Unauthorized", "status": 401,
             "detail": "Missing or wrong registration token in the X-Registration-Token header.",
             "instance": "/internal/v1/workers/register"}""";

    private static final String BAD_SECRET_EXAMPLE = """
            {"type": "about:blank", "title": "Unauthorized", "status": 401,
             "detail": "Missing, unknown or replaced worker secret. Register again.",
             "instance": "/internal/v1/workers/3/heartbeat"}""";

    private static final String OTHER_WORKER_EXAMPLE = """
            {"type": "about:blank", "title": "Forbidden", "status": 403,
             "detail": "This secret belongs to worker 4, not 3", "instance": "/internal/v1/workers/3/heartbeat"}""";

    private static final String VALIDATION_EXAMPLE = """
            {"type": "about:blank", "title": "Bad Request", "status": 400, "detail": "Validation failed",
             "instance": "/internal/v1/workers/register", "errors": [{"field": "cpus", "message": "must be greater than or equal to 1"}]}""";

    @Schema(description = "Who the worker is and what it can use")
    record RegisterWorkerRequest(
            @Schema(
                            example = "worker-1",
                            description = "Unique worker name (the container host name by default). A known name "
                                    + "reuses its record: a restarted worker keeps its id.")
                    @NotBlank
                    @Size(max = 100)
                    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]*", message = "may contain letters, digits, '.', '_' and '-'")
                    String name,
            @Schema(example = "0.1.0", description = "Worker software version") @NotBlank @Size(max = 64) String version,
            @Schema(example = "2", description = "CPUs the worker can use") @NotNull @Min(1) @Max(1024) Integer cpus,
            @Schema(example = "4096", description = "Memory the worker can use, MB") @NotNull @Min(1) @Max(Integer.MAX_VALUE)
                    Long memMb) {}

    @Schema(description = "The worker's own state")
    record HeartbeatRequest(
            @Schema(example = "ONLINE", description = "ONLINE (free) or BUSY (runs a segment)", allowableValues = {"ONLINE", "BUSY"})
                    @NotNull
                    WorkerStatus status,
            @Schema(nullable = true, description = "Segment the worker runs (from Sprint 2)") Long activeSegment) {}

    private final WorkerService workerService;

    WorkerProtocolController(WorkerService workerService) {
        this.workerService = workerService;
    }

    @PostMapping("/register")
    @Operation(
            summary = "Register a worker",
            description = "Exchanges the shared registration token for a worker id and a per-worker secret, which "
                    + "every later call sends as `Authorization: Bearer <secret>`. The secret is returned only "
                    + "here; registering again (after a restart) gives a new one and the old one stops working. "
                    + "The worker is ONLINE at once.",
            security = @SecurityRequirement(name = REGISTRATION_TOKEN))
    @ApiResponse(
            responseCode = "200",
            description = "Registered",
            content = @Content(
                    schema = @Schema(implementation = RegisterResponse.class),
                    examples = @ExampleObject(value = """
                            {"workerId": 3, "workerSecret": "q8V1o2m3J5f4pYbW0wK9xZt7sLr6cNd2eHgUaQiE1Ok"}""")))
    @ApiResponse(
            responseCode = "400",
            description = "Invalid request",
            content = @Content(
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = VALIDATION_EXAMPLE)))
    @ApiResponse(
            responseCode = "401",
            description = "Missing or wrong registration token; no worker is created",
            content = @Content(
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = BAD_TOKEN_EXAMPLE)))
    RegisterResponse register(@Valid @RequestBody RegisterWorkerRequest request) {
        return workerService.register(
                request.name(), request.cpus(), Math.toIntExact(request.memMb()), request.version());
    }

    @PostMapping("/{id}/heartbeat")
    @Operation(
            summary = "Send a heartbeat",
            description = "Every 2 s. Updates the last heartbeat time (Control Service clock) and the status; a "
                    + "LOST or OFFLINE worker becomes ONLINE again. A worker without a heartbeat for more than "
                    + "15 s is marked LOST. The answer carries commands for the worker (ASSIGN, CANCEL; from "
                    + "Sprint 2, empty until then). On 401 the worker registers again.",
            security = @SecurityRequirement(name = WORKER_SECRET))
    @ApiResponse(
            responseCode = "200",
            description = "Heartbeat recorded",
            content = @Content(
                    schema = @Schema(implementation = HeartbeatResponse.class),
                    examples = @ExampleObject(value = "{\"commands\": []}")))
    @ApiResponse(
            responseCode = "401",
            description = "Missing, unknown or replaced secret",
            content = @Content(
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = BAD_SECRET_EXAMPLE)))
    @ApiResponse(
            responseCode = "403",
            description = "The secret belongs to another worker",
            content = @Content(
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = OTHER_WORKER_EXAMPLE)))
    HeartbeatResponse heartbeat(
            @Parameter(in = ParameterIn.PATH, example = "3") @PathVariable long id,
            @AuthenticationPrincipal WorkerPrincipal worker,
            @Valid @RequestBody HeartbeatRequest request) {
        workerService.heartbeat(worker, id, request.status());
        return new HeartbeatResponse(List.of());
    }

    @PostMapping("/{id}/deregister")
    @Operation(
            summary = "Stop cleanly",
            description = "Called when the worker shuts down. It shows OFFLINE at once instead of LOST after the "
                    + "heartbeat timeout. A later heartbeat or registration makes it ONLINE again.",
            security = @SecurityRequirement(name = WORKER_SECRET))
    @ApiResponse(responseCode = "204", description = "The worker is OFFLINE")
    @ApiResponse(
            responseCode = "401",
            description = "Missing, unknown or replaced secret",
            content = @Content(
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = BAD_SECRET_EXAMPLE)))
    @ApiResponse(
            responseCode = "403",
            description = "The secret belongs to another worker",
            content = @Content(
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = OTHER_WORKER_EXAMPLE)))
    ResponseEntity<Void> deregister(
            @Parameter(in = ParameterIn.PATH, example = "3") @PathVariable long id,
            @AuthenticationPrincipal WorkerPrincipal worker) {
        workerService.deregister(worker, id);
        return ResponseEntity.noContent().build();
    }
}
