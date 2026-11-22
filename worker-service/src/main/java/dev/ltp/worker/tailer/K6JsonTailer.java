package dev.ltp.worker.tailer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Follows the k6 JSON output file ({@code --out json=…}) while k6 is still writing it (T-05).
 *
 * <p>Each call to {@link #poll()} reads the bytes appended since the previous call and returns the
 * points of the metrics the platform uses. An incomplete last line is kept until its newline arrives.
 * The file may not exist yet when k6 is still starting.
 *
 * <p>Line format (k6 v2, same as v1): {@code {"metric":"http_req_duration","type":"Point",
 * "data":{"time":"2026-…Z","value":6.2,"tags":{…}}}}. {@code "type":"Metric"} lines only declare
 * metrics and are skipped.
 */
public final class K6JsonTailer {

    static final Set<String> METRICS =
            Set.of(K6Sample.HTTP_REQ_DURATION, K6Sample.HTTP_REQS, K6Sample.HTTP_REQ_FAILED, K6Sample.VUS);

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String POINT_PREFIX = "{\"metric\":\"";
    private static final int CHUNK = 64 * 1024;

    private final Path file;
    private final ByteArrayOutputStream partialLine = new ByteArrayOutputStream();
    private long position;
    private long lines;
    private long malformedLines;

    public K6JsonTailer(Path file) {
        this.file = file;
    }

    /** Returns the samples of all complete lines appended since the previous call. */
    public List<K6Sample> poll() throws IOException {
        List<K6Sample> samples = new ArrayList<>();
        if (!Files.exists(file)) {
            return samples;
        }
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            byte[] chunk = new byte[CHUNK];
            ByteBuffer buffer = ByteBuffer.wrap(chunk);
            channel.position(position);
            int read;
            while ((read = channel.read(buffer)) > 0) {
                position += read;
                int lineStart = 0;
                for (int i = 0; i < read; i++) {
                    if (chunk[i] == '\n') {
                        partialLine.write(chunk, lineStart, i - lineStart);
                        parseLine(partialLine.toString(StandardCharsets.UTF_8), samples);
                        partialLine.reset();
                        lineStart = i + 1;
                    }
                }
                partialLine.write(chunk, lineStart, read - lineStart);
                buffer.clear();
            }
        } catch (NoSuchFileException e) {
            // deleted between exists() and open(): nothing to read
        }
        return samples;
    }

    /** Complete lines read so far, of any metric. */
    public long lines() {
        return lines;
    }

    /** Lines that were not valid k6 JSON (skipped). */
    public long malformedLines() {
        return malformedLines;
    }

    private void parseLine(String line, List<K6Sample> samples) {
        if (line.isBlank()) {
            return;
        }
        lines++;
        // Fast path: k6 writes points as {"metric":"<name>",…}; skip unused metrics without parsing
        // (9 of the 13 lines per request). Other shapes fall through to the full parse.
        if (line.startsWith(POINT_PREFIX)) {
            int end = line.indexOf('"', POINT_PREFIX.length());
            if (end > 0 && !METRICS.contains(line.substring(POINT_PREFIX.length(), end))) {
                return;
            }
        }
        try {
            JsonNode node = JSON.readTree(line);
            if (!"Point".equals(node.path("type").asString())) {
                return;
            }
            String metric = node.path("metric").asString();
            if (!METRICS.contains(metric)) {
                return;
            }
            JsonNode data = node.path("data");
            samples.add(new K6Sample(metric, Instant.parse(data.path("time").asString()), data.path("value").asDouble()));
        } catch (JacksonException | DateTimeParseException e) {
            malformedLines++;
        }
    }
}
