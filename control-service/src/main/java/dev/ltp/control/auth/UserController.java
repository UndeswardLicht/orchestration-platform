package dev.ltp.control.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** User management (US-23). Admins only: other roles get 403. */
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Users", description = "Accounts and roles (admin only)")
class UserController {

    private static final String USER_EXAMPLE = """
            {"id": 2, "login": "dana", "email": "dana@example.org", "role": "DEVELOPER",
             "createdAt": "2026-11-25T09:30:00Z"}""";

    private static final String VALIDATION_EXAMPLE = """
            {"type": "about:blank", "title": "Bad Request", "status": 400, "detail": "Validation failed",
             "instance": "/api/v1/users", "errors": [{"field": "password", "message": "size must be between 12 and 72"}]}""";

    private static final String FORBIDDEN_EXAMPLE = """
            {"type": "about:blank", "title": "Forbidden", "status": 403,
             "detail": "Your role does not allow this action.", "instance": "/api/v1/users"}""";

    @Schema(description = "A new account")
    record CreateUserRequest(
            @Schema(example = "dana", description = "Letters, digits and . _ @ -; unique ignoring case")
                    @NotBlank
                    @Size(max = 64)
                    @Pattern(regexp = "^\\s*[A-Za-z0-9._@-]+\\s*$", message = "may contain only letters, digits and . _ @ -")
                    String login,
            @Schema(example = "dana@example.org", nullable = true) @Email @Size(max = 254) String email,
            @Schema(example = "a-long-initial-password", description = "12 to 72 characters (at most 72 bytes)")
                    @NotNull
                    @Size(min = UserService.MIN_PASSWORD_LENGTH, max = UserService.MAX_PASSWORD_BYTES)
                    String password,
            @Schema(example = "DEVELOPER") @NotNull Role role) {}

    @Schema(description = "The new role")
    record ChangeRoleRequest(@Schema(example = "VIEWER") @NotNull Role role) {}

    @Schema(description = "An account")
    record UserResponse(
            @Schema(example = "2") long id,
            @Schema(example = "dana") String login,
            @Schema(example = "dana@example.org", nullable = true) String email,
            Role role,
            @Schema(example = "2026-11-25T09:30:00Z") Instant createdAt) {

        static UserResponse of(User user) {
            return new UserResponse(
                    user.getId(), user.getLogin(), user.getEmail(), user.getRole(), user.getCreatedAt());
        }
    }

    private final UserService userService;

    UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "List users", description = "All active accounts, ordered by login.")
    @ApiResponse(
            responseCode = "200",
            description = "Users",
            content = @Content(
                    array = @ArraySchema(schema = @Schema(implementation = UserResponse.class)),
                    examples = @ExampleObject(value = "[" + USER_EXAMPLE + "]")))
    @ApiResponse(
            responseCode = "403",
            description = "Not an admin",
            content = @Content(
                    mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = FORBIDDEN_EXAMPLE)))
    List<UserResponse> list() {
        return userService.listActive().stream().map(UserResponse::of).toList();
    }

    @PostMapping
    @Operation(
            summary = "Create a user",
            description = "Creates an account with an initial password and a role.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(examples = @ExampleObject(
                            name = "developer",
                            value = """
                                    {"login": "dana", "email": "dana@example.org",
                                     "password": "a-long-initial-password", "role": "DEVELOPER"}"""))))
    @ApiResponse(
            responseCode = "201",
            description = "Created",
            content = @Content(
                    schema = @Schema(implementation = UserResponse.class),
                    examples = @ExampleObject(value = USER_EXAMPLE)))
    @ApiResponse(
            responseCode = "400",
            description = "A field is missing or invalid; `errors` names it",
            content = @Content(
                    mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = VALIDATION_EXAMPLE)))
    @ApiResponse(responseCode = "403", description = "Not an admin", content = @Content)
    @ApiResponse(responseCode = "409", description = "The login is taken", content = @Content)
    ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest body) {
        User user = userService.create(body.login(), body.email(), body.password(), body.role());
        return ResponseEntity.created(URI.create("/api/v1/users/" + user.getId())).body(UserResponse.of(user));
    }

    @PatchMapping("/{id}")
    @Operation(
            summary = "Change a user's role",
            description = "The user's open sessions end at once, so the new role applies on the next sign-in. "
                    + "The last admin cannot lose the ADMIN role.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(examples = @ExampleObject(name = "viewer", value = """
                            {"role": "VIEWER"}"""))))
    @ApiResponse(
            responseCode = "200",
            description = "Role changed",
            content = @Content(
                    schema = @Schema(implementation = UserResponse.class),
                    examples = @ExampleObject(value = USER_EXAMPLE)))
    @ApiResponse(responseCode = "403", description = "Not an admin", content = @Content)
    @ApiResponse(responseCode = "404", description = "No such active user", content = @Content)
    @ApiResponse(responseCode = "409", description = "The user is the last admin", content = @Content)
    UserResponse changeRole(
            @Parameter(description = "User id", example = "2") @PathVariable long id,
            @Valid @RequestBody ChangeRoleRequest body) {
        return UserResponse.of(userService.changeRole(id, body.role()));
    }
}
