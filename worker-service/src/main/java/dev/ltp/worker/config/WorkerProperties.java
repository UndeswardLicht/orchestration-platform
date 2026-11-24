package dev.ltp.worker.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Worker settings ({@code ltp.worker.*}).
 *
 * @param controlUrl base URL of the Control Service
 * @param registrationToken shared token from {@code .env}, exchanged for a per-worker secret
 * @param name worker name shown in the UI
 * @param k6Image pinned k6 image (risk R8)
 * @param ioVolume mount source shared by the worker and k6 containers (scripts + JSON output): a
 *     named Docker volume, or an absolute host path (bind mount, used by tests)
 * @param ioDir where {@code ioVolume} is visible to the worker process
 * @param k6Network bridge network for k6 containers (never {@code host})
 * @param k6Cpus CPU limit per k6 container (NFR-05: 1 CPU)
 * @param k6MemoryMb memory limit per k6 container, swap included (NFR-05: 512 MB)
 * @param heartbeatInterval heartbeat period (NFR-04: 2 s)
 */
@Validated
@ConfigurationProperties("ltp.worker")
public record WorkerProperties(
        @NotNull URI controlUrl,
        @NotBlank String registrationToken,
        @NotBlank String name,
        @NotBlank String k6Image,
        @NotBlank String ioVolume,
        @NotBlank String ioDir,
        @NotBlank String k6Network,
        @Positive double k6Cpus,
        @Positive long k6MemoryMb,
        @NotNull Duration heartbeatInterval) {}
