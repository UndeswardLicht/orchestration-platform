package dev.ltp.worker.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
 * @param ioVolume named Docker volume shared by the worker and k6 containers (scripts + JSON output)
 * @param ioDir where {@code ioVolume} is mounted inside the worker container
 * @param k6Network bridge network for k6 containers (never {@code host})
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
        @NotNull Duration heartbeatInterval) {}
