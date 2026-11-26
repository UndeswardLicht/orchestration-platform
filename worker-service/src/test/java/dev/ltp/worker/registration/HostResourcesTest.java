package dev.ltp.worker.registration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HostResourcesTest {

    @Test
    void detectsWhatTheJvmCanUse() {
        HostResources resources = HostResources.detect();

        assertThat(resources.cpus()).isEqualTo(Runtime.getRuntime().availableProcessors());
        // The JVM's heap limit is part of the memory it can see
        assertThat(resources.memMb()).isGreaterThanOrEqualTo(Runtime.getRuntime().maxMemory() / (1024 * 1024));
    }

    @Test
    void hidesTheSecretInLogs() {
        assertThat(new Registration(3, "secret-3").toString()).doesNotContain("secret-3");
    }
}
