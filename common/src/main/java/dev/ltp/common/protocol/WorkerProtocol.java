package dev.ltp.common.protocol;

import java.util.List;
import java.util.Map;

/**
 * DTOs of the Worker ↔ Control pull protocol (05-tech-stack.md §4).
 *
 * <p>Workers always initiate the connection:
 *
 * <ul>
 *   <li>{@code POST /internal/v1/workers/register} — {@link RegisterRequest} → {@link
 *       RegisterResponse}
 *   <li>{@code POST /internal/v1/workers/{id}/heartbeat} — {@link HeartbeatRequest} → {@link
 *       HeartbeatResponse}
 *   <li>{@code GET /internal/v1/scenario-versions/{id}/script} — script text
 *   <li>{@code POST /internal/v1/segments/{segmentId}/metrics} — {@link MetricsBatch}
 *   <li>{@code POST /internal/v1/segments/{segmentId}/finish} — {@link FinishRequest}
 * </ul>
 */
public final class WorkerProtocol {

    public static final String BASE_PATH = "/internal/v1";
    public static final String REGISTRATION_TOKEN_HEADER = "X-Registration-Token";

    private WorkerProtocol() {}

    public enum WorkerStatus {
        ONLINE,
        BUSY,
        LOST,
        OFFLINE
    }

    public record RegisterRequest(String name, String version, int cpus, long memMb) {}

    public record RegisterResponse(long workerId, String workerSecret) {}

    public record HeartbeatRequest(WorkerStatus status, Long activeSegment) {}

    public record HeartbeatResponse(List<Command> commands) {
        public HeartbeatResponse {
            commands = commands == null ? List.of() : List.copyOf(commands);
        }
    }

    /** Commands returned to a worker in a heartbeat response. */
    public sealed interface Command permits Assign, Cancel {}

    /**
     * Run one execution segment of a run.
     *
     * @param segment k6 {@code --execution-segment} value, e.g. {@code 1/4:2/4}
     * @param sequence k6 {@code --execution-segment-sequence} value, e.g. {@code 0,1/4,2/4,3/4,1}
     */
    public record Assign(
            long runId,
            long segmentId,
            int attempt,
            String scriptRef,
            String segment,
            String sequence,
            List<String> k6Args,
            Map<String, String> env)
            implements Command {
        public Assign {
            k6Args = k6Args == null ? List.of() : List.copyOf(k6Args);
            env = env == null ? Map.of() : Map.copyOf(env);
        }
    }

    public record Cancel(long segmentId) implements Command {}

    /**
     * One second of metrics from one worker.
     *
     * @param t second offset from the run start
     * @param digest base64 of a serialized t-digest of {@code http_req_duration} (ms), see {@code
     *     dev.ltp.common.digest.DigestCodec}
     */
    public record SecondMetrics(long t, String digest, long reqs, long failed, int vus) {}

    /** Also counts as a heartbeat for the sending worker. */
    public record MetricsBatch(int attempt, List<SecondMetrics> seconds) {
        public MetricsBatch {
            seconds = seconds == null ? List.of() : List.copyOf(seconds);
        }
    }

    public record FinishRequest(int attempt, int exitCode, String k6Summary) {}
}
