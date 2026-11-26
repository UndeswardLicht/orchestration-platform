package dev.ltp.control.scenario;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** One version in the version list of a scenario (US-02), without the script. */
@Schema(description = "A version of a scenario, without its script")
public record ScenarioVersionSummary(
        @Schema(example = "32", description = "Id of the version (runs reference it)",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                long id,
        @Schema(example = "2", requiredMode = Schema.RequiredMode.REQUIRED) int version,
        @Schema(
                        example = "b5af96aa2912f2e473d65d41c193309718c093667197a98c99048d97b482a240",
                        description = "SHA-256 of the script (UTF-8), lower-case hex",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String sha256,
        @Schema(example = "dana", requiredMode = Schema.RequiredMode.REQUIRED) String createdBy,
        @Schema(example = "2026-11-27T10:15:00Z", requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt) {}
