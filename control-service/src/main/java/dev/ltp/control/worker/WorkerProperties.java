package dev.ltp.control.worker;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Worker settings of the Control Service ({@code ltp.worker.*}, US-06).
 *
 * @param registrationToken shared token from {@code .env}; a worker exchanges it for its own secret
 * @param heartbeatTimeout a worker without a heartbeat for longer than this is LOST (NFR-04: 15 s)
 */
@Validated
@ConfigurationProperties("ltp.worker")
public record WorkerProperties(@NotBlank String registrationToken, @NotNull Duration heartbeatTimeout) {}
