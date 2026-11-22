package dev.ltp.worker.runner;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ltp.common.protocol.WorkerProtocol.Assign;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class K6CommandTest {

    @Test
    void passesSegmentTagsOutputAndControlArgumentsBeforeTheScript() {
        Assign assign =
                new Assign(7, 3, 1, "v2", "1/4:2/4", "0,1/4,2/4,3/4,1", List.of("--vus", "100", "--rps", "250"), Map.of());

        assertThat(K6ContainerRunner.command(assign, "/k6-io/run-7/seg-3-a1"))
                .containsExactly(
                        "run",
                        "--quiet",
                        "--execution-segment", "1/4:2/4",
                        "--execution-segment-sequence", "0,1/4,2/4,3/4,1",
                        "--system-tags", "status,expected_response",
                        "--out", "json=/k6-io/run-7/seg-3-a1/out.json",
                        "--vus", "100",
                        "--rps", "250",
                        "/k6-io/run-7/seg-3-a1/script.js");
    }

    @Test
    void absolutePathsAreBindMountsAndNamesAreVolumes() {
        assertThat(K6ContainerRunner.isHostPath("/tmp/junit123")).isTrue();
        assertThat(K6ContainerRunner.isHostPath("C:\\Users\\me\\tmp")).isTrue();
        assertThat(K6ContainerRunner.isHostPath("D:/data")).isTrue();
        assertThat(K6ContainerRunner.isHostPath("ltp-k6-io")).isFalse();
    }
}
