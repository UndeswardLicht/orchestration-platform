package dev.ltp.control.scenario;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** One row of the scenario list (US-01), without the script. */
@Schema(description = "A scenario in the list, without its script")
public record ScenarioSummary(
        @Schema(example = "12", requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @Schema(example = "Checkout API", requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(example = "Browse, add to cart, pay", nullable = true) String description,
        @Schema(
                        example = "1",
                        description = "Number of the newest version",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                int latestVersion,
        @Schema(example = "dana", description = "Login of the user who created the scenario",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String createdBy,
        @Schema(example = "2026-11-26T09:30:00Z", requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(
                        example = "2026-11-26T09:30:00Z",
                        description = "When the newest version was saved",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Instant updatedAt) {}
