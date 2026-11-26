package dev.ltp.control.scenario;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** One scenario with its newest version and script (US-01; US-02 adds the list of versions). */
@Schema(description = "A scenario with its newest version")
public record ScenarioDetails(
        @Schema(example = "12", requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @Schema(example = "Checkout API", requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(example = "Browse, add to cart, pay", nullable = true) String description,
        @Schema(example = "dana", requiredMode = Schema.RequiredMode.REQUIRED) String createdBy,
        @Schema(example = "2026-11-26T09:30:00Z", requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Version latestVersion) {

    /** One immutable version of the script. */
    @Schema(name = "ScenarioVersionDetails", description = "One immutable version of a scenario's script")
    public record Version(
            @Schema(example = "31", description = "Id of the version (runs reference it)",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    long id,
            @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) int version,
            @Schema(
                            example = "import http from 'k6/http';\nexport default function () {\n"
                                    + "  http.get(__ENV.BASE_URL + '/');\n}\n",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    String script,
            @Schema(
                            example = "b5af96aa2912f2e473d65d41c193309718c093667197a98c99048d97b482a240",
                            description = "SHA-256 of the script (UTF-8), lower-case hex",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    String sha256,
            @Schema(example = "dana", requiredMode = Schema.RequiredMode.REQUIRED) String createdBy,
            @Schema(example = "2026-11-26T09:30:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
                    Instant createdAt) {

        static Version of(ScenarioVersion version) {
            return new Version(
                    version.getId(),
                    version.getVersion(),
                    version.getScript(),
                    version.getSha256(),
                    version.getCreatedBy().getLogin(),
                    version.getCreatedAt());
        }
    }

    static ScenarioDetails of(Scenario scenario, ScenarioVersion latest) {
        return new ScenarioDetails(
                scenario.getId(),
                scenario.getName(),
                scenario.getDescription(),
                scenario.getCreatedBy().getLogin(),
                scenario.getCreatedAt(),
                Version.of(latest));
    }
}
