package dev.ltp.control.scenario;

import dev.ltp.control.auth.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Scenarios from k6 scripts (US-01). Every role may read; creating needs DEVELOPER or ADMIN (US-23).
 * The script is plain JSON text: the web app reads an uploaded file in the browser and sends its text.
 */
@RestController
@RequestMapping("/api/v1/scenarios")
@Tag(name = "Scenarios", description = "Named k6 scripts with immutable versions")
class ScenarioController {

    private static final String SUMMARY_EXAMPLE = """
            {"id": 12, "name": "Checkout API", "description": "Browse, add to cart, pay", "latestVersion": 1,
             "createdBy": "dana", "createdAt": "2026-11-26T09:30:00Z", "updatedAt": "2026-11-26T09:30:00Z"}""";

    private static final String DETAILS_EXAMPLE = """
            {"id": 12, "name": "Checkout API", "description": "Browse, add to cart, pay", "createdBy": "dana",
             "createdAt": "2026-11-26T09:30:00Z",
             "latestVersion": {"id": 31, "version": 1,
               "script": "import http from 'k6/http';\\nexport default function () {\\n  http.get(__ENV.BASE_URL + '/');\\n}\\n",
               "sha256": "b5af96aa2912f2e473d65d41c193309718c093667197a98c99048d97b482a240",
               "createdBy": "dana", "createdAt": "2026-11-26T09:30:00Z"}}""";

    private static final String VALIDATION_EXAMPLE = """
            {"type": "about:blank", "title": "Bad Request", "status": 400, "detail": "Validation failed",
             "instance": "/api/v1/scenarios", "errors": [{"field": "script", "message": "must not be blank"}]}""";

    private static final String FORBIDDEN_EXAMPLE = """
            {"type": "about:blank", "title": "Forbidden", "status": 403,
             "detail": "Your role does not allow this action.", "instance": "/api/v1/scenarios"}""";

    private static final String CONFLICT_EXAMPLE = """
            {"type": "about:blank", "title": "Conflict", "status": 409,
             "detail": "A scenario named 'Checkout API' already exists", "instance": "/api/v1/scenarios"}""";

    private static final String NOT_FOUND_EXAMPLE = """
            {"type": "about:blank", "title": "Not Found", "status": 404,
             "detail": "No scenario with id 12", "instance": "/api/v1/scenarios/12"}""";

    @Schema(description = "A new scenario and the script of its version 1")
    record CreateScenarioRequest(
            @Schema(example = "Checkout API", description = "Unique ignoring case; surrounding spaces are removed")
                    @NotBlank
                    @Size(max = 200)
                    String name,
            @Schema(example = "Browse, add to cart, pay", nullable = true) @Size(max = 2000) String description,
            @Schema(
                            example = "import http from 'k6/http';\nexport default function () {\n"
                                    + "  http.get(__ENV.BASE_URL + '/');\n}\n",
                            description = "k6 script (JavaScript), stored exactly as sent; at most 1 MB in UTF-8. "
                                    + "The run's target URL is passed to it as the BASE_URL environment variable.")
                    @NotBlank
                    String script) {}

    private final ScenarioService scenarioService;

    ScenarioController(ScenarioService scenarioService) {
        this.scenarioService = scenarioService;
    }

    @GetMapping
    @Operation(
            summary = "List scenarios",
            description = "Every scenario with its newest version number, ordered by name. Scripts are left out.")
    @ApiResponse(
            responseCode = "200",
            description = "Scenarios",
            content = @Content(
                    array = @ArraySchema(schema = @Schema(implementation = ScenarioSummary.class)),
                    examples = @ExampleObject(value = "[" + SUMMARY_EXAMPLE + "]")))
    List<ScenarioSummary> list() {
        return scenarioService.list();
    }

    @PostMapping
    @PreAuthorize("hasRole('DEVELOPER')")
    @Operation(
            summary = "Create a scenario",
            description = "Saves the scenario and version 1 of its k6 script. Needs the DEVELOPER or ADMIN role.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(examples = @ExampleObject(
                            name = "checkout",
                            value = """
                                    {"name": "Checkout API", "description": "Browse, add to cart, pay",
                                     "script": "import http from 'k6/http';\\nexport default function () {\\n  http.get(__ENV.BASE_URL + '/');\\n}\\n"}"""))))
    @ApiResponse(
            responseCode = "201",
            description = "Created with version 1",
            content = @Content(
                    schema = @Schema(implementation = ScenarioSummary.class),
                    examples = @ExampleObject(value = SUMMARY_EXAMPLE)))
    @ApiResponse(
            responseCode = "400",
            description = "Name or script is empty, too long or too large; `errors` names the field",
            content = @Content(
                    mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = VALIDATION_EXAMPLE)))
    @ApiResponse(
            responseCode = "403",
            description = "Viewers cannot create scenarios",
            content = @Content(
                    mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = FORBIDDEN_EXAMPLE)))
    @ApiResponse(
            responseCode = "409",
            description = "A scenario with this name exists",
            content = @Content(
                    mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = CONFLICT_EXAMPLE)))
    ResponseEntity<ScenarioSummary> create(
            @Valid @RequestBody CreateScenarioRequest body, @AuthenticationPrincipal AuthenticatedUser user) {
        ScenarioSummary created = scenarioService.create(body.name(), body.description(), body.script(), user.id());
        return ResponseEntity.created(URI.create("/api/v1/scenarios/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a scenario", description = "The scenario with its newest version, script included.")
    @ApiResponse(
            responseCode = "200",
            description = "The scenario",
            content = @Content(
                    schema = @Schema(implementation = ScenarioDetails.class),
                    examples = @ExampleObject(value = DETAILS_EXAMPLE)))
    @ApiResponse(
            responseCode = "404",
            description = "No such scenario",
            content = @Content(
                    mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = NOT_FOUND_EXAMPLE)))
    ScenarioDetails get(@Parameter(description = "Scenario id", example = "12") @PathVariable long id) {
        return scenarioService.get(id);
    }
}
