package dev.ltp.worker.runner;

import java.nio.file.Path;

/**
 * A k6 container started for one segment attempt.
 *
 * @param dir worker-side directory of this attempt (script and output)
 * @param outputFile worker-side path of the k6 JSON output that the tailer follows
 */
public record K6Segment(
        long runId, long segmentId, int attempt, String containerId, Path dir, Path outputFile) {}
