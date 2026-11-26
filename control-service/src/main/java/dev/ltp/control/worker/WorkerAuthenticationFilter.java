package dev.ltp.control.worker;

import dev.ltp.common.protocol.WorkerProtocol;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates workers on {@code /internal/**} (US-06, NFR-05). Registration needs the shared
 * registration token in {@value WorkerProtocol#REGISTRATION_TOKEN_HEADER}; every other call needs the
 * per-worker secret as {@code Authorization: Bearer <secret>}. A request without valid credentials stays
 * anonymous, and the security chain answers it with 401.
 *
 * <p>Not a Spring bean on purpose: a bean filter would also run for the public API.
 */
public class WorkerAuthenticationFilter extends OncePerRequestFilter {

    public static final String ROLE_REGISTRAR = "WORKER_REGISTRAR";
    public static final String ROLE_WORKER = "WORKER";

    public static final RequestMatcher REGISTRATION =
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, WorkerProtocol.BASE_PATH + "/workers/register");

    private static final String BEARER = "Bearer ";

    private final SecurityContextHolderStrategy contexts = SecurityContextHolder.getContextHolderStrategy();
    private final WorkerRepository workers;
    private final WorkerProperties properties;

    public WorkerAuthenticationFilter(WorkerRepository workers, WorkerProperties properties) {
        this.workers = workers;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (REGISTRATION.matches(request)) {
            String token = request.getHeader(WorkerProtocol.REGISTRATION_TOKEN_HEADER);
            if (WorkerSecrets.matches(token, properties.registrationToken())) {
                authenticate(new UsernamePasswordAuthenticationToken(
                        "registration", null, List.of(new SimpleGrantedAuthority("ROLE_" + ROLE_REGISTRAR))));
            }
        } else {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null && header.startsWith(BEARER)) {
                String secret = header.substring(BEARER.length()).trim();
                workers.findBySecretHash(WorkerSecrets.hash(secret))
                        .ifPresent(worker -> authenticate(new UsernamePasswordAuthenticationToken(
                                new WorkerPrincipal(worker.getId(), worker.getName()),
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + ROLE_WORKER)))));
            }
        }
        chain.doFilter(request, response);
    }

    private void authenticate(UsernamePasswordAuthenticationToken authentication) {
        SecurityContext context = contexts.createEmptyContext();
        context.setAuthentication(authentication);
        contexts.setContext(context);
    }
}
