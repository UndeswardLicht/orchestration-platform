package dev.ltp.common.digest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.tdunning.math.stats.TDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class DigestCodecTest {

    @Test
    void base64RoundTripKeepsQuantiles() {
        TDigest digest = DigestCodec.newDigest();
        Random random = new Random(42);
        for (int i = 0; i < 10_000; i++) {
            digest.add(Math.exp(3 + 0.5 * random.nextGaussian()));
        }

        TDigest restored = DigestCodec.fromBase64(DigestCodec.toBase64(digest));

        assertThat(restored.size()).isEqualTo(digest.size());
        assertThat(restored.quantile(0.95)).isCloseTo(digest.quantile(0.95), within(1e-6));
    }

    /** Workers with different latency: merged digest stays within 5 % of the exact pooled p95/p99. */
    @Test
    void mergedDigestMatchesPooledPercentiles() {
        Random random = new Random(7);
        double[] medians = {20, 25, 40, 80};
        List<TDigest> perWorker = new ArrayList<>();
        List<Double> pooled = new ArrayList<>();

        for (double median : medians) {
            TDigest digest = DigestCodec.newDigest();
            for (int i = 0; i < 20_000; i++) {
                double v = median * Math.exp(0.4 * random.nextGaussian());
                if (random.nextDouble() < 0.02) {
                    v *= 10; // slow tail
                }
                digest.add(v);
                pooled.add(v);
            }
            perWorker.add(DigestCodec.fromBytes(DigestCodec.toBytes(digest)));
        }

        TDigest merged = DigestCodec.merge(perWorker);
        double[] sorted = pooled.stream().mapToDouble(Double::doubleValue).sorted().toArray();

        for (double q : new double[] {0.95, 0.99}) {
            double exact = exactQuantile(sorted, q);
            assertThat(merged.quantile(q)).isCloseTo(exact, within(exact * 0.05));
        }
    }

    private static double exactQuantile(double[] sorted, double q) {
        int index = (int) Math.ceil(q * sorted.length) - 1;
        return sorted[Math.max(index, 0)];
    }
}
