package dev.ltp.control.scenario;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Result of saving a script (US-02): a new version, or the latest one when the script was identical
 * and nothing was saved.
 */
@Schema(description = "Result of saving a script")
public record SavedVersion(
        @Schema(
                        example = "true",
                        description = "true: a new version was saved; false: the script equals the latest version, "
                                + "nothing was saved and `version` is that latest version",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                boolean created,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ScenarioDetails.Version version) {}
