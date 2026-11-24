package dev.ltp.worker;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ltp.worker.config.WorkerProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class WorkerServiceApplicationTests {

    @Autowired
    WorkerProperties properties;

    @Test
    void bindsDefaultsWithPinnedK6Image() {
        assertThat(properties.k6Image()).matches("grafana/k6:\\d+\\.\\d+\\.\\d+");
        assertThat(properties.heartbeatInterval()).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void limitsK6ContainersTo1CpuAnd512MbByDefault() {
        // NFR-05
        assertThat(properties.k6Cpus()).isEqualTo(1.0);
        assertThat(properties.k6MemoryMb()).isEqualTo(512);
    }
}
