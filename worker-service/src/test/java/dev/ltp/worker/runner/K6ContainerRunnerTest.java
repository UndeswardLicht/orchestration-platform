package dev.ltp.worker.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.HostConfig;
import com.tdunning.math.stats.TDigest;
import dev.ltp.common.digest.DigestCodec;
import dev.ltp.common.protocol.WorkerProtocol.Assign;
import dev.ltp.common.protocol.WorkerProtocol.SecondMetrics;
import dev.ltp.worker.config.DockerConfig;
import dev.ltp.worker.tailer.K6JsonTailer;
import dev.ltp.worker.tailer.K6Sample;
import dev.ltp.worker.tailer.SecondAggregator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * SPIKE-1 / US-08: k6 v2 in a limited container on segment {@code 0:1/2}, JSON output on the shared
 * mount, per-second t-digests built while k6 runs. Needs Docker (ADR-0004).
 */
class K6ContainerRunnerTest {

    /** The pinned image from the parent POM ({@code k6.image}, risk R8), passed in by Surefire. */
    static final String K6_IMAGE =
            Objects.requireNonNull(System.getProperty("ltp.k6.image"), "run through Maven: -Dltp.k6.image is set by Surefire");
    static final String TARGET_IMAGE = "caddy:2.11.9-alpine";
    static final Duration GRACE = Duration.ofSeconds(1);

    static DockerClient docker;
    static String network;
    static String targetName;
    static String targetId;

    @TempDir
    Path io;

    @BeforeAll
    static void startTarget() throws Exception {
        docker = DockerConfig.createClient();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        network = docker.createNetworkCmd().withName("ltp-test-" + suffix).withDriver("bridge").exec().getId();
        targetName = "ltp-test-target-" + suffix;
        pullIfMissing(TARGET_IMAGE);
        targetId =
                docker.createContainerCmd(TARGET_IMAGE)
                        .withName(targetName)
                        .withCmd("caddy", "respond", "--listen", ":8080", "ok")
                        .withHostConfig(HostConfig.newHostConfig().withNetworkMode(network))
                        .exec()
                        .getId();
        docker.startContainerCmd(targetId).exec();
        await().atMost(Duration.ofSeconds(30)).until(K6ContainerRunnerTest::targetAnswers);
    }

    @AfterAll
    static void stopTarget() throws IOException {
        if (targetId != null) {
            docker.removeContainerCmd(targetId).withForce(true).exec();
        }
        if (network != null) {
            docker.removeNetworkCmd(network).exec();
        }
        docker.close();
    }

    @Test
    void runsSegmentInLimitedContainerAndBuildsPerSecondDigests() throws Exception {
        K6ContainerRunner runner = runner();
        // The script asks for 10 VUs for 1 min; Control's CLI flags must win (k6 v2 override check)
        String script =
                """
                import http from 'k6/http';
                export const options = { vus: 10, duration: '1m' };
                export default function () { http.get(`${__ENV.BASE_URL}/`); }
                """;
        Assign assign =
                new Assign(
                        1, 1, 1, "v1", "0:1/2", "0,1/2,1", // 400 RPS: a realistic per-worker rate (NFR-06 allows 1 000 for the whole cluster)
                        List.of("--vus", "4", "--duration", "6s", "--rps", "400"),
                        Map.of("BASE_URL", "http://" + targetName + ":8080"));

        Instant runStart = Instant.now();
        K6Segment segment = runner.start(assign, script);
        try {
            assertIsolated(docker.inspectContainerCmd(segment.containerId()).exec(), segment);

            K6JsonTailer tailer = new K6JsonTailer(segment.outputFile());
            SecondAggregator aggregator = new SecondAggregator(runStart);
            List<SecondMetrics> seconds = new ArrayList<>();
            List<Long> lagMillis = new ArrayList<>();
            List<Instant> emittedAt = new ArrayList<>();
            while (runner.isRunning(segment)) {
                collect(tailer, aggregator, lagMillis);
                List<SecondMetrics> done = aggregator.drainCompleted(GRACE);
                done.forEach(s -> emittedAt.add(Instant.now()));
                seconds.addAll(done);
                TimeUnit.MILLISECONDS.sleep(200);
            }
            int exitCode = runner.awaitExit(segment, Duration.ofSeconds(30));
            collect(tailer, aggregator, lagMillis);
            seconds.addAll(aggregator.drainAll());

            assertThat(exitCode).isZero();
            assertThat(tailer.malformedLines()).isZero();
            assertThat(aggregator.lateSamples()).isZero();
            // Segment 0:1/2 of --vus 4 → 2 VUs (US-05); --duration 6s overrides the script's 1m
            assertThat(seconds.stream().mapToInt(SecondMetrics::vus).max().orElse(0)).isEqualTo(2);
            assertThat(seconds).hasSizeBetween(6, 9);
            assertThat(seconds.getFirst().t()).isGreaterThanOrEqualTo(0);

            RawSeconds raw = RawSeconds.read(segment.outputFile(), runStart);
            assertThat(seconds.stream().mapToLong(SecondMetrics::reqs).sum()).isEqualTo(raw.requests());
            assertThat(seconds.stream().mapToLong(SecondMetrics::failed).sum()).isZero();

            int compared = 0;
            for (SecondMetrics second : seconds) {
                double[] values = raw.durations(second.t());
                TDigest digest = DigestCodec.fromBase64(second.digest());
                assertThat(digest.size()).isEqualTo(values.length);
                if (values.length >= 200) {
                    double exact = exactQuantile(values, 0.95);
                    // NFR-03: the platform's percentile is within 5 % of the exact one
                    assertThat(digest.quantile(0.95)).isCloseTo(exact, org.assertj.core.data.Percentage.withPercentage(5));
                    compared++;
                }
            }
            assertThat(compared).as("seconds with enough samples to compare").isGreaterThanOrEqualTo(3);

            assertThat(aggregator.maxDisorder()).as("k6 writes samples roughly in time order").isLessThan(GRACE);
            report(seconds, lagMillis, raw, segment, aggregator, emittedAt, runStart);
        } finally {
            runner.remove(segment);
        }

        assertThatThrownBy(() -> docker.inspectContainerCmd(segment.containerId()).exec())
                .isInstanceOf(NotFoundException.class);
        assertThat(segment.dir()).doesNotExist();
    }

    @Test
    void stopEndsALongRunWithinTenSeconds() throws Exception {
        K6ContainerRunner runner = runner();
        String script =
                """
                import http from 'k6/http';
                export default function () { http.get(`${__ENV.BASE_URL}/`); }
                """;
        Assign assign =
                new Assign(
                        2, 5, 1, "v1", "0:1", "0,1", List.of("--vus", "1", "--duration", "5m"),
                        Map.of("BASE_URL", "http://" + targetName + ":8080"));
        K6Segment segment = runner.start(assign, script);
        try {
            await().atMost(Duration.ofSeconds(30)).until(() -> Files.exists(segment.outputFile()) && Files.size(segment.outputFile()) > 0);

            Instant stopAt = Instant.now();
            runner.stop(segment, Duration.ofSeconds(3));
            int exitCode = runner.awaitExit(segment, Duration.ofSeconds(10));
            Duration took = Duration.between(stopAt, Instant.now());

            // US-04: all containers stop within 10 s
            assertThat(took).isLessThan(Duration.ofSeconds(10));
            System.out.printf("[SPIKE-1] stop: k6 exited with code %d after %d ms%n", exitCode, took.toMillis());
        } finally {
            runner.remove(segment);
        }
    }

    @Test
    void removesLeftoverContainers() {
        K6ContainerRunner runner = runner();
        Assign assign = new Assign(3, 9, 1, "v1", "0:1", "0,1", List.of("--vus", "1", "--duration", "5m"), Map.of("BASE_URL", "http://" + targetName + ":8080"));
        K6Segment segment = runner.start(assign, "export default function () {}");

        assertThat(runner.removeLeftovers()).isGreaterThanOrEqualTo(1);
        assertThatThrownBy(() -> docker.inspectContainerCmd(segment.containerId()).exec())
                .isInstanceOf(NotFoundException.class);
        runner.remove(segment);
    }

    private K6ContainerRunner runner() {
        // Bind mount of a temp dir stands in for the named volume used in Compose
        return new K6ContainerRunner(docker, K6_IMAGE, io.toAbsolutePath().toString(), io, network, 1.0, 512);
    }

    private static void assertIsolated(InspectContainerResponse container, K6Segment segment) {
        HostConfig host = container.getHostConfig();
        // NFR-05: 1 CPU and 512 MB by default
        assertThat(host.getNanoCPUs()).isEqualTo(1_000_000_000L);
        assertThat(host.getMemory()).isEqualTo(512L * 1024 * 1024);
        assertThat(host.getMemorySwap()).isEqualTo(512L * 1024 * 1024);
        assertThat(host.getPidsLimit()).isEqualTo(K6ContainerRunner.PIDS_LIMIT);
        assertThat(host.getReadonlyRootfs()).isTrue();
        assertThat(host.getPrivileged()).isFalse();
        assertThat(host.getCapDrop()).containsExactly(Capability.ALL);
        assertThat(host.getSecurityOpts()).contains("no-new-privileges");
        assertThat(host.getNetworkMode()).isEqualTo(network).isNotEqualTo("host");
        assertThat(container.getConfig().getLabels())
                .containsEntry(K6ContainerRunner.LABEL_MANAGED, "true")
                .containsEntry(K6ContainerRunner.LABEL_RUN_ID, Long.toString(segment.runId()))
                .containsEntry(K6ContainerRunner.LABEL_SEGMENT_ID, Long.toString(segment.segmentId()));
        assertThat(container.getConfig().getImage()).isEqualTo(K6_IMAGE);
    }

    private static void collect(K6JsonTailer tailer, SecondAggregator aggregator, List<Long> lagMillis) throws IOException {
        List<K6Sample> samples = tailer.poll();
        Instant now = Instant.now();
        for (K6Sample sample : samples) {
            if (K6Sample.HTTP_REQ_DURATION.equals(sample.metric())) {
                lagMillis.add(Duration.between(sample.time(), now).toMillis());
            }
        }
        aggregator.acceptAll(samples);
    }

    /** numpy's default ("linear") percentile definition, used for KPI-2 references. */
    static double exactQuantile(double[] values, double q) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        double pos = q * (sorted.length - 1);
        int lower = (int) Math.floor(pos);
        int upper = Math.min(lower + 1, sorted.length - 1);
        return sorted[lower] + (pos - lower) * (sorted[upper] - sorted[lower]);
    }

    private static void report(
            List<SecondMetrics> seconds, List<Long> lagMillis, RawSeconds raw, K6Segment segment,
            SecondAggregator aggregator, List<Instant> emittedAt, Instant runStart) throws IOException {
        long[] lags = lagMillis.stream().mapToLong(Long::longValue).sorted().toArray();
        long bytes = Files.size(segment.outputFile());
        System.out.printf(
                "[SPIKE-1] %d requests, %d JSON bytes (%d per request), read lag p50=%d ms p95=%d ms max=%d ms%n",
                raw.requests(), bytes, bytes / Math.max(1, raw.requests()), lags[lags.length / 2], lags[(int) (lags.length * 0.95)], lags[lags.length - 1]);
        System.out.printf("[SPIKE-1] max sample disorder %d ms%n", aggregator.maxDisorder().toMillis());
        for (int i = 0; i < emittedAt.size(); i++) {
            long secondEnd = runStart.toEpochMilli() + (seconds.get(i).t() + 1) * 1000;
            System.out.printf("[SPIKE-1] second %d emitted %d ms after its end%n", seconds.get(i).t(), emittedAt.get(i).toEpochMilli() - secondEnd);
        }
        long start = System.nanoTime();
        K6JsonTailer again = new K6JsonTailer(segment.outputFile());
        again.poll();
        double secs = (System.nanoTime() - start) / 1e9;
        System.out.printf("[SPIKE-1] tailer re-read %d lines in %.0f ms = %.0f lines/s%n", again.lines(), secs * 1000, again.lines() / secs);
        for (SecondMetrics s : seconds) {
            TDigest d = DigestCodec.fromBase64(s.digest());
            System.out.printf(
                    "[SPIKE-1] t=%d reqs=%d vus=%d p95=%.3f ms exact=%.3f ms%n",
                    s.t(), s.reqs(), s.vus(), d.size() == 0 ? Double.NaN : d.quantile(0.95),
                    raw.durations(s.t()).length == 0 ? Double.NaN : exactQuantile(raw.durations(s.t()), 0.95));
        }
    }

    private static boolean targetAnswers() {
        try {
            String exec =
                    docker.execCreateCmd(targetId).withCmd("wget", "-q", "-O", "-", "http://localhost:8080/").exec().getId();
            docker.execStartCmd(exec).start().awaitCompletion(5, TimeUnit.SECONDS);
            Long code = docker.inspectExecCmd(exec).exec().getExitCodeLong();
            return code != null && code == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static void pullIfMissing(String image) throws InterruptedException {
        try {
            docker.inspectImageCmd(image).exec();
        } catch (NotFoundException e) {
            int colon = image.lastIndexOf(':');
            docker.pullImageCmd(image.substring(0, colon)).withTag(image.substring(colon + 1)).start().awaitCompletion(5, TimeUnit.MINUTES);
        }
    }

    /** Raw k6 points re-read from the output file: the exact reference for each second. */
    record RawSeconds(Map<Long, double[]> durationsBySecond, long requests) {

        static RawSeconds read(Path file, Instant runStart) throws IOException {
            List<K6Sample> samples = new K6JsonTailer(file).poll();
            Map<Long, List<Double>> bySecond = new TreeMap<>();
            long requests = 0;
            for (K6Sample s : samples) {
                if (K6Sample.HTTP_REQ_DURATION.equals(s.metric())) {
                    long t = Math.floorDiv(s.time().toEpochMilli() - runStart.toEpochMilli(), 1000);
                    bySecond.computeIfAbsent(t, k -> new ArrayList<>()).add(s.value());
                } else if (K6Sample.HTTP_REQS.equals(s.metric())) {
                    requests += (long) s.value();
                }
            }
            Map<Long, double[]> arrays = new TreeMap<>();
            bySecond.forEach((t, v) -> arrays.put(t, v.stream().mapToDouble(Double::doubleValue).toArray()));
            return new RawSeconds(arrays, requests);
        }

        double[] durations(long t) {
            return durationsBySecond.getOrDefault(t, new double[0]);
        }
    }
}
