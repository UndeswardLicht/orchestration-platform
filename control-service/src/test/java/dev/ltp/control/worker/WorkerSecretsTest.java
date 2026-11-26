package dev.ltp.control.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkerSecretsTest {

    @Test
    void secretsAreLongRandomAndUrlSafe() {
        Set<String> secrets = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            secrets.add(WorkerSecrets.generate());
        }
        assertThat(secrets).hasSize(100).allMatch(secret -> secret.matches("[A-Za-z0-9_-]{43}"));
    }

    @Test
    void theHashIsTheHexSha256() {
        assertThat(WorkerSecrets.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void matchesComparesWholeValues() {
        assertThat(WorkerSecrets.matches("change-me", "change-me")).isTrue();
        assertThat(WorkerSecrets.matches("change-me-not", "change-me")).isFalse();
        assertThat(WorkerSecrets.matches("change", "change-me")).isFalse();
        assertThat(WorkerSecrets.matches(null, "change-me")).isFalse();
    }
}
