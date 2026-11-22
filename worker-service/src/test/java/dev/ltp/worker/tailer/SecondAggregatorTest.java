package dev.ltp.worker.tailer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.ltp.common.digest.DigestCodec;
import dev.ltp.common.protocol.WorkerProtocol.SecondMetrics;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SecondAggregatorTest {

    private static final Instant START = Instant.parse("2026-11-22T10:00:00.500Z");

    @Test
    void bucketsSamplesBySecondOfTheRun() {
        SecondAggregator aggregator = new SecondAggregator(START);
        // second 0 = [10:00:00.500, 10:00:01.500)
        aggregator.accept(new K6Sample(K6Sample.HTTP_REQ_DURATION, START.plusMillis(10), 10));
        aggregator.accept(new K6Sample(K6Sample.HTTP_REQS, START.plusMillis(10), 1));
        aggregator.accept(new K6Sample(K6Sample.HTTP_REQ_FAILED, START.plusMillis(10), 0));
        aggregator.accept(new K6Sample(K6Sample.HTTP_REQ_DURATION, START.plusMillis(999), 30));
        aggregator.accept(new K6Sample(K6Sample.HTTP_REQS, START.plusMillis(999), 1));
        aggregator.accept(new K6Sample(K6Sample.HTTP_REQ_FAILED, START.plusMillis(999), 1));
        aggregator.accept(new K6Sample(K6Sample.VUS, START.plusMillis(900), 3));
        aggregator.accept(new K6Sample(K6Sample.VUS, START.plusMillis(950), 2));
        // second 1
        aggregator.accept(new K6Sample(K6Sample.HTTP_REQ_DURATION, START.plusMillis(1000), 50));
        aggregator.accept(new K6Sample(K6Sample.HTTP_REQS, START.plusMillis(1000), 1));

        List<SecondMetrics> seconds = aggregator.drainAll();

        assertThat(seconds).extracting(SecondMetrics::t).containsExactly(0L, 1L);
        SecondMetrics first = seconds.getFirst();
        assertThat(first.reqs()).isEqualTo(2);
        assertThat(first.failed()).isEqualTo(1);
        assertThat(first.vus()).isEqualTo(3);
        assertThat(DigestCodec.fromBase64(first.digest()).size()).isEqualTo(2);
        assertThat(DigestCodec.fromBase64(first.digest()).getMax()).isEqualTo(30);
        assertThat(DigestCodec.fromBase64(seconds.get(1).digest()).getMin()).isEqualTo(50);
    }

    @Test
    void emitsASecondOnceASampleGraceAfterItsEndWasRead() {
        SecondAggregator aggregator = new SecondAggregator(START);
        Duration grace = Duration.ofSeconds(1);
        aggregator.accept(new K6Sample(K6Sample.HTTP_REQS, START.plusMillis(100), 1));
        aggregator.accept(new K6Sample(K6Sample.HTTP_REQS, START.plusMillis(1100), 1));

        // second 0 ends at START + 1 s; it is complete when a sample at START + 2 s or later was read
        assertThat(aggregator.drainCompleted(grace)).isEmpty();
        aggregator.accept(new K6Sample(K6Sample.VUS, START.plusMillis(1999), 1));
        assertThat(aggregator.drainCompleted(grace)).isEmpty();
        aggregator.accept(new K6Sample(K6Sample.VUS, START.plusMillis(2000), 1));
        assertThat(aggregator.drainCompleted(grace)).extracting(SecondMetrics::t).containsExactly(0L);
        assertThat(aggregator.drainCompleted(grace)).isEmpty();
    }

    @Test
    void waitsForTheDataNotTheClockWhenReadingFallsBehind() {
        SecondAggregator aggregator = new SecondAggregator(START);
        aggregator.accept(new K6Sample(K6Sample.HTTP_REQS, START.plusMillis(100), 1));

        // However much wall-clock time passes, second 0 stays open until later data is read
        assertThat(aggregator.drainCompleted(Duration.ofSeconds(1))).isEmpty();
        aggregator.accept(new K6Sample(K6Sample.HTTP_REQS, START.plusMillis(900), 1));
        aggregator.accept(new K6Sample(K6Sample.HTTP_REQS, START.plusMillis(2500), 1));

        SecondMetrics first = aggregator.drainCompleted(Duration.ofSeconds(1)).getFirst();
        assertThat(first.reqs()).isEqualTo(2);
        assertThat(aggregator.lateSamples()).isZero();
    }

    @Test
    void dropsAndCountsSamplesForSecondsAlreadySent() {
        SecondAggregator aggregator = new SecondAggregator(START);
        aggregator.accept(new K6Sample(K6Sample.HTTP_REQS, START.plusMillis(100), 1));
        aggregator.accept(new K6Sample(K6Sample.VUS, START.plusSeconds(5), 1));
        aggregator.drainCompleted(Duration.ofSeconds(1));

        aggregator.accept(new K6Sample(K6Sample.HTTP_REQS, START.plusMillis(200), 1));
        aggregator.accept(new K6Sample(K6Sample.HTTP_REQS, START.plusMillis(3100), 1));

        assertThat(aggregator.lateSamples()).isEqualTo(2);
        assertThat(aggregator.maxDisorder()).isEqualTo(Duration.ofMillis(4800));
        assertThat(aggregator.drainAll()).extracting(SecondMetrics::t).containsExactly(5L);
    }

    @Test
    void digestOfASecondGivesItsP95() {
        SecondAggregator aggregator = new SecondAggregator(START);
        for (int i = 1; i <= 1000; i++) {
            aggregator.accept(new K6Sample(K6Sample.HTTP_REQ_DURATION, START.plusMillis(i % 1000), i));
        }

        SecondMetrics second = aggregator.drainAll().getFirst();

        assertThat(DigestCodec.fromBase64(second.digest()).quantile(0.95)).isCloseTo(950.5, within(950.5 * 0.05));
    }
}
