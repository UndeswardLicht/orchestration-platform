package dev.ltp.control.config;

import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionLimit;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.session.HttpSessionEventPublisher;

/**
 * Security of the public API (US-23, NFR-05): deny by default, session cookie sign-in, CSRF cookie for
 * the web app, and the role ladder ADMIN > DEVELOPER > VIEWER.
 *
 * <p>Rules on the HTTP level: reading {@code /api/**} needs VIEWER, every change needs DEVELOPER.
 * Endpoints tighten this with {@code @PreAuthorize} (for example ADMIN for user management), so a new
 * write endpoint without an annotation is still closed to Viewers.
 *
 * <p>Later: US-24 adds the API-token filter, US-06 a separate chain for {@code /internal/**}.
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(LtpSecurityProperties.class)
public class SecurityConfig {

    /** Name of the session cookie; also set in {@code server.servlet.session.cookie.name}. */
    public static final String SESSION_COOKIE = "LTP_SESSION";

    /** The cookie the web app reads and sends back in the {@value #CSRF_HEADER} header. */
    public static final String CSRF_COOKIE = "XSRF-TOKEN";

    public static final String CSRF_HEADER = "X-XSRF-TOKEN";

    /** Reachable without signing in. Everything else returns 401 to anonymous callers (NFR-05). */
    public static final List<String> PUBLIC_PATHS = List.of(
            "/actuator/health",
            "/actuator/health/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/api/v1/auth/login",
            "/api/v1/auth/logout",
            "/api/v1/auth/csrf");

    /**
     * Salted password hashes with an algorithm prefix, bcrypt by default (NFR-05). The prefix lets a
     * later switch to argon2 keep old hashes valid; the database accepts only these prefixes.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /** ADMIN may do everything a DEVELOPER may, and a DEVELOPER everything a VIEWER may. */
    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("ADMIN")
                .implies("DEVELOPER")
                .role("DEVELOPER")
                .implies("VIEWER")
                .build();
    }

    /**
     * Checks login and password against the {@code users} table. Unknown logins and wrong passwords
     * fail the same way and take about the same time (no way to find out which logins exist).
     */
    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(), new HttpSessionSecurityContextRepository());
    }

    /** CSRF token in a cookie the web app can read; Secure and SameSite=Lax like the session cookie. */
    @Bean
    CookieCsrfTokenRepository csrfTokenRepository(LtpSecurityProperties properties) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieName(CSRF_COOKIE);
        repository.setHeaderName(CSRF_HEADER);
        repository.setCookiePath("/");
        repository.setCookieCustomizer(cookie -> cookie.sameSite("Lax").secure(properties.secureCookies()));
        return repository;
    }

    /** Active sessions per user, so a role change can end that user's sessions at once. */
    @Bean
    SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /** Tells the session registry when sessions end (logout, timeout). */
    @Bean
    HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    /**
     * What happens to the session on a successful login: a new session ID (session fixation), the
     * session is registered for its user, and a new CSRF token is issued.
     */
    @Bean
    SessionAuthenticationStrategy loginSessionStrategy(
            SessionRegistry sessionRegistry, CookieCsrfTokenRepository csrfTokenRepository) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(),
                new RegisterSessionAuthenticationStrategy(sessionRegistry),
                new CsrfAuthenticationStrategy(csrfTokenRepository)));
    }

    @Bean
    SecurityFilterChain apiSecurity(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository,
            CookieCsrfTokenRepository csrfTokenRepository,
            SessionRegistry sessionRegistry,
            ProblemResponseWriter problems)
            throws Exception {
        http.securityContext(context -> context.securityContextRepository(securityContextRepository))
                .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository))
                .sessionManagement(session -> session.sessionConcurrency(concurrency -> concurrency
                        .maximumSessions(SessionLimit.UNLIMITED)
                        .sessionRegistry(sessionRegistry)
                        .expiredSessionStrategy(event -> problems.write(
                                event.getRequest(),
                                event.getResponse(),
                                HttpStatus.UNAUTHORIZED,
                                "Your access has changed. Sign in again."))))
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, e) ->
                                problems.write(request, response, HttpStatus.UNAUTHORIZED, "Sign in first."))
                        .accessDeniedHandler((request, response, e) -> problems.write(
                                request,
                                response,
                                HttpStatus.FORBIDDEN,
                                e instanceof CsrfException
                                        ? "Missing or invalid CSRF token. Send the XSRF-TOKEN cookie value in the "
                                                + CSRF_HEADER + " header."
                                        : "Your role does not allow this action.")))
                // Logout is a documented endpoint (AuthController), not Spring's built-in /logout
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(auth -> auth.requestMatchers(PUBLIC_PATHS.toArray(String[]::new))
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/**")
                        .hasRole("VIEWER")
                        .requestMatchers("/api/**")
                        .hasRole("DEVELOPER")
                        .requestMatchers("/actuator/**")
                        .hasRole("ADMIN")
                        .anyRequest()
                        .authenticated());
        return http.build();
    }
}
