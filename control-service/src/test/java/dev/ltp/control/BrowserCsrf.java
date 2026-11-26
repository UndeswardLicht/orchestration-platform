package dev.ltp.control;

import dev.ltp.control.config.SecurityConfig;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Sends a CSRF token the way the web app does: the same value in the XSRF-TOKEN cookie and the
 * X-XSRF-TOKEN header (double-submit cookie).
 *
 * <p>Used instead of Spring Security's {@code SecurityMockMvcRequestPostProcessors.csrf()}, which swaps
 * the CSRF token store inside the shared filter chain. Afterwards the real cookie is no longer written,
 * which breaks other tests that use the same Spring context, including those over real HTTP.
 */
public final class BrowserCsrf {

    public static final String TOKEN = "test-csrf-token";

    private BrowserCsrf() {}

    public static RequestPostProcessor browserCsrf() {
        return request -> {
            Cookie[] existing = request.getCookies();
            Cookie[] cookies = existing == null ? new Cookie[1] : Arrays.copyOf(existing, existing.length + 1);
            cookies[cookies.length - 1] = new Cookie(SecurityConfig.CSRF_COOKIE, TOKEN);
            request.setCookies(cookies);
            request.addHeader(SecurityConfig.CSRF_HEADER, TOKEN);
            return request;
        };
    }
}
