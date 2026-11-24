package dev.ltp.common.segment;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a run into {@code n} equal k6 execution segments (US-05).
 *
 * <p>Worker {@code i} of {@code n} runs {@code --execution-segment i/n:(i+1)/n} with {@code
 * --execution-segment-sequence 0,1/n,…,1}; k6 then distributes VUs and iterations across segments
 * without overlap.
 */
public final class SegmentMath {

    private SegmentMath() {}

    /** Segment for worker {@code index} (0-based) of {@code count}, e.g. {@code 1/4:2/4}. */
    public static String segment(int index, int count) {
        validate(index, count);
        return fraction(index, count) + ":" + fraction(index + 1, count);
    }

    /** Full segment sequence for {@code count} workers, e.g. {@code 0,1/4,2/4,3/4,1}. */
    public static String sequence(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("count must be >= 1, was " + count);
        }
        List<String> points = new ArrayList<>(count + 1);
        for (int i = 0; i <= count; i++) {
            points.add(fraction(i, count));
        }
        return String.join(",", points);
    }

    private static String fraction(int numerator, int denominator) {
        if (numerator == 0) {
            return "0";
        }
        if (numerator == denominator) {
            return "1";
        }
        return numerator + "/" + denominator;
    }

    private static void validate(int index, int count) {
        if (count < 1) {
            throw new IllegalArgumentException("count must be >= 1, was " + count);
        }
        if (index < 0 || index >= count) {
            throw new IllegalArgumentException(
                    "index must be in [0, " + count + "), was " + index);
        }
    }
}
