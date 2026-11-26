package dev.ltp.control.auth;

import dev.ltp.control.config.ApiException;
import dev.ltp.control.config.SecurityConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfLogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sign-in for the web app (US-23): JSON login → HTTP-only session cookie, plus a CSRF cookie that the
 * web app sends back in the {@code X-XSRF-TOKEN} header on every change.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Sign in and out with a session cookie")
class AuthController {

    private static final String CURRENT_USER_EXAMPLE = """
            {"id": 1, "login": "admin", "role": "ADMIN"}""";

    private static final String BAD_LOGIN_EXAMPLE = """
            {"type": "about:blank", "title": "Unauthorized", "status": 401,
             "detail": "Invalid login or password", "instance": "/api/v1/auth/login"}""";

    @Schema(description = "Login and password")
    record LoginRequest(
            @Schema(example = "admin") @NotBlank String login,
            @Schema(example = "correct-horse-battery-staple") @NotBlank String password) {}

    @Schema(description = "The signed-in user")
    record CurrentUserResponse(
            @Schema(example = "1") long id, @Schema(example = "admin") String login, Role role) {

        static CurrentUserResponse of(AuthenticatedUser user) {
            return new CurrentUserResponse(user.id(), user.login(), user.role());
        }
    }

    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();
    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy loginSessionStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final CookieCsrfTokenRepository csrfTokenRepository;

    AuthController(
            AuthenticationManager authenticationManager,
            SessionAuthenticationStrategy loginSessionStrategy,
            SecurityContextRepository securityContextRepository,
            CookieCsrfTokenRepository csrfTokenRepository) {
        this.authenticationManager = authenticationManager;
        this.loginSessionStrategy = loginSessionStrategy;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    @GetMapping("/csrf")
    @SecurityRequirements
    @Operation(
            summary = "Issue a CSRF token",
            description = "Sets the XSRF-TOKEN cookie. Send its value in the X-XSRF-TOKEN header with every "
                    + "POST, PATCH, PUT and DELETE, including the login itself. Call it once before signing in.")
    @ApiResponse(responseCode = "204", description = "Cookie set")
    ResponseEntity<Void> csrf(CsrfToken token) {
        token.getToken(); // loading the deferred token writes the cookie
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(
            summary = "Sign in",
            description = "Checks login and password. On success the response sets the HTTP-only session cookie "
                    + SecurityConfig.SESSION_COOKIE + " and a new XSRF-TOKEN cookie.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(examples = @ExampleObject(
                            name = "admin",
                            value = """
                                    {"login": "admin", "password": "correct-horse-battery-staple"}"""))))
    @ApiResponse(
            responseCode = "200",
            description = "Signed in",
            content = @Content(
                    schema = @Schema(implementation = CurrentUserResponse.class),
                    examples = @ExampleObject(value = CURRENT_USER_EXAMPLE)))
    @ApiResponse(
            responseCode = "401",
            description = "Wrong login or password (the same answer for both)",
            content = @Content(
                    mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = BAD_LOGIN_EXAMPLE)))
    CurrentUserResponse login(
            @Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(body.login().strip(), body.password()));
        } catch (AuthenticationException e) {
            throw ApiException.unauthorized("Invalid login or password");
        }
        loginSessionStrategy.onAuthentication(authentication, request, response);
        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        renderNewCsrfToken(request);
        return CurrentUserResponse.of((AuthenticatedUser) authentication.getPrincipal());
    }

    @PostMapping("/logout")
    @SecurityRequirements
    @Operation(
            summary = "Sign out",
            description = "Ends the session and deletes the session and CSRF cookies. Succeeds also when "
                    + "not signed in.")
    @ApiResponse(responseCode = "204", description = "Signed out")
    ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = contextHolder.getContext().getAuthentication();
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        new CsrfLogoutHandler(csrfTokenRepository).logout(request, response, authentication);
        new CookieClearingLogoutHandler(SecurityConfig.SESSION_COOKIE).logout(request, response, authentication);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(
            summary = "Who am I",
            description = "The signed-in user and role. The web app calls it after a page reload.")
    @ApiResponse(
            responseCode = "200",
            description = "Signed in",
            content = @Content(
                    schema = @Schema(implementation = CurrentUserResponse.class),
                    examples = @ExampleObject(value = CURRENT_USER_EXAMPLE)))
    @ApiResponse(responseCode = "401", description = "Not signed in", content = @Content)
    CurrentUserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return CurrentUserResponse.of(user);
    }

    /** The login replaced the CSRF token; load the new one so its cookie is part of this response. */
    private static void renderNewCsrfToken(HttpServletRequest request) {
        if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token) {
            token.getToken();
        }
    }
}
