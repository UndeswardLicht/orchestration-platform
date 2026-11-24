package dev.ltp.common.segment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SegmentMathTest {

    @Test
    void singleWorkerRunsWholeTest() {
        assertThat(SegmentMath.segment(0, 1)).isEqualTo("0:1");
        assertThat(SegmentMath.sequence(1)).isEqualTo("0,1");
    }

    @Test
    void fourWorkersGetAdjacentNonOverlappingSegments() {
        assertThat(SegmentMath.segment(0, 4)).isEqualTo("0:1/4");
        assertThat(SegmentMath.segment(1, 4)).isEqualTo("1/4:2/4");
        assertThat(SegmentMath.segment(3, 4)).isEqualTo("3/4:1");
        assertThat(SegmentMath.sequence(4)).isEqualTo("0,1/4,2/4,3/4,1");
    }

    @Test
    void rejectsInvalidArguments() {
        assertThatThrownBy(() -> SegmentMath.segment(4, 4))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SegmentMath.sequence(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
