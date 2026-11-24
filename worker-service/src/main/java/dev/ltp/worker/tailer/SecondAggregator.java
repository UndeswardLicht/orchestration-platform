package dev.ltp.worker.tailer;

import com.tdunning.math.stats.TDigest;
import dev.ltp.common.digest.DigestCodec;
import dev.ltp.common.protocol.WorkerProtocol.SecondMetrics;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Buckets k6 samples into seconds of the run: one t-digest of {@code http_req_duration} plus counters
 * per second (T-05, US-10).
 *
 * <p>Second {@code t} covers {@code [runStart + t s, runStart + (t + 1) s)}. Every worker uses the
 * run start sent by Control, so second {@code t} means the same wall-clock second on all workers, and
 * Control can merge their digests per second (never averaging percentiles).
 *
 * <p>Completeness is driven by the data, not the wall clock: a second is complete once a sample at
 * least {@code grace} after its end has been read (the <em>watermark</em>). k6 writes samples
 * roughly in time order, and its {@code vus} gauge keeps the watermark moving even when no request
 * finishes. If the tailer falls behind, seconds come late but complete (ADR-0007). Samples that
 * arrive for a second already sent are counted as late and dropped.
 */
public final class SecondAggregator {

    private final long runStartMillis;
    private final NavigableMap<Long, Bucket> open = new TreeMap<>();
    /** All seconds before this one have been emitted. */
    private long completeBefore = Long.MIN_VALUE;
    private long watermarkMillis = Long.MIN_VALUE;
    private long lateSamples;
    private long maxDisorderMillis;

    public SecondAggregator(Instant runStart) {
        this.runStartMillis = runStart.toEpochMilli();
    }

    public void accept(K6Sample sample) {
        long time = sample.time().toEpochMilli();
        if (watermarkMillis != Long.MIN_VALUE) {
            maxDisorderMillis = Math.max(maxDisorderMillis, watermarkMillis - time);
        }
        watermarkMillis = Math.max(watermarkMillis, time);
        long t = Math.floorDiv(time - runStartMillis, 1000);
        if (t < completeBefore) {
            lateSamples++;
            return;
        }
        Bucket bucket = open.computeIfAbsent(t, k -> new Bucket());
        switch (sample.metric()) {
            case K6Sample.HTTP_REQ_DURATION -> bucket.digest.add(sample.value());
            case K6Sample.HTTP_REQS -> bucket.reqs += (long) sample.value();
            case K6Sample.HTTP_REQ_FAILED -> bucket.failed += (long) sample.value();
            case K6Sample.VUS -> bucket.vus = Math.max(bucket.vus, (int) sample.value());
            default -> {
                // not a metric the platform uses
            }
        }
    }

    public void acceptAll(List<K6Sample> samples) {
        samples.forEach(this::accept);
    }

    /** Emits every second that ends at least {@code grace} before the watermark, oldest first. */
    public List<SecondMetrics> drainCompleted(Duration grace) {
        if (watermarkMillis == Long.MIN_VALUE) {
            return List.of();
        }
        long before = Math.floorDiv(watermarkMillis - grace.toMillis() - runStartMillis, 1000);
        completeBefore = Math.max(completeBefore, before);
        return drain(open.headMap(completeBefore, false));
    }

    /** Emits all remaining seconds, e.g. after k6 has exited. */
    public List<SecondMetrics> drainAll() {
        if (!open.isEmpty()) {
            completeBefore = Math.max(completeBefore, open.lastKey() + 1);
        }
        return drain(open);
    }

    /** Samples dropped because their second had already been emitted. */
    public long lateSamples() {
        return lateSamples;
    }

    /** Largest amount by which a sample was older than the newest sample read before it. */
    public Duration maxDisorder() {
        return Duration.ofMillis(maxDisorderMillis);
    }

    private static List<SecondMetrics> drain(Map<Long, Bucket> seconds) {
        List<SecondMetrics> result = new ArrayList<>(seconds.size());
        seconds.forEach(
                (t, b) -> result.add(new SecondMetrics(t, DigestCodec.toBase64(b.digest), b.reqs, b.failed, b.vus)));
        seconds.clear();
        return result;
    }

    private static final class Bucket {
        final TDigest digest = DigestCodec.newDigest();
        long reqs;
        long failed;
        int vus;
    }
}
