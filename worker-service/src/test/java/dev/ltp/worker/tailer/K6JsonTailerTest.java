package dev.ltp.worker.tailer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class K6JsonTailerTest {

    // Real k6 v2.4.0 lines (SPIKE-1), tags trimmed by --system-tags status,expected_response
    private static final String METRIC_DECLARATION =
            "{\"type\":\"Metric\",\"data\":{\"name\":\"http_reqs\",\"type\":\"counter\",\"contains\":\"default\","
                    + "\"thresholds\":[],\"submetrics\":null},\"metric\":\"http_reqs\"}\n";
    private static final String DURATION =
            "{\"metric\":\"http_req_duration\",\"type\":\"Point\",\"data\":{\"time\":\"2026-11-22T19:06:11.150421608Z\","
                    + "\"value\":0.724694,\"tags\":{\"expected_response\":\"true\",\"status\":\"200\"}}}\n";
    private static final String BLOCKED =
            "{\"metric\":\"http_req_blocked\",\"type\":\"Point\",\"data\":{\"time\":\"2026-11-22T19:06:11.150421608Z\","
                    + "\"value\":0.01,\"tags\":{\"status\":\"200\"}}}\n";
    private static final String VUS =
            "{\"metric\":\"vus\",\"type\":\"Point\",\"data\":{\"time\":\"2026-11-22T19:06:12.000000001Z\",\"value\":2,\"tags\":{}}}\n";

    @TempDir
    Path dir;

    @Test
    void returnsNothingBeforeK6CreatesTheFile() throws IOException {
        assertThat(new K6JsonTailer(dir.resolve("out.json")).poll()).isEmpty();
    }

    @Test
    void readsOnlyPointsOfUsedMetrics() throws IOException {
        Path file = dir.resolve("out.json");
        Files.writeString(file, METRIC_DECLARATION + DURATION + BLOCKED + VUS);

        K6JsonTailer tailer = new K6JsonTailer(file);

        assertThat(tailer.poll())
                .containsExactly(
                        new K6Sample("http_req_duration", Instant.parse("2026-11-22T19:06:11.150421608Z"), 0.724694),
                        new K6Sample("vus", Instant.parse("2026-11-22T19:06:12.000000001Z"), 2));
        assertThat(tailer.lines()).isEqualTo(4);
    }

    @Test
    void followsTheFileAndWaitsForTheEndOfALine() throws IOException {
        Path file = dir.resolve("out.json");
        K6JsonTailer tailer = new K6JsonTailer(file);
        int half = DURATION.length() / 2;

        append(file, DURATION.substring(0, half));
        assertThat(tailer.poll()).isEmpty();

        append(file, DURATION.substring(half) + VUS.substring(0, 10));
        assertThat(tailer.poll()).extracting(K6Sample::metric).containsExactly("http_req_duration");

        append(file, VUS.substring(10));
        assertThat(tailer.poll()).extracting(K6Sample::metric).containsExactly("vus");
        assertThat(tailer.poll()).isEmpty();
    }

    @Test
    void skipsMalformedLines() throws IOException {
        Path file = dir.resolve("out.json");
        Files.writeString(file, "not json\n" + DURATION);

        K6JsonTailer tailer = new K6JsonTailer(file);

        assertThat(tailer.poll()).hasSize(1);
        assertThat(tailer.malformedLines()).isEqualTo(1);
    }

    private static void append(Path file, String text) throws IOException {
        Files.writeString(
                file, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
