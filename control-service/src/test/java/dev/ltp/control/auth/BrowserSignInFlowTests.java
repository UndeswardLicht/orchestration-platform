package dev.ltp.control.auth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ltp.control.IntegrationTest;
import dev.ltp.control.config.SecurityConfig;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;

/**
 * The sign-in flow as a browser does it, over real HTTP: the cookie attributes come from Tomcat, which
 * MockMvc does not run. Cookies are handled by hand because Java's cookie store drops Secure cookies
 * on plain-HTTP test connections.
 */
@IntegrationTest
class BrowserSignInFlowTests {

    @LocalServerPort
    int port;

    @Autowired
    Environment environment;

    final HttpClient http = HttpClient.newHttpClient();

    @Test
    void signInWithCsrfCookieThenUseTheSessionCookie() throws Exception {
        // 1. The web app asks for a CSRF token
        HttpResponse<String> csrf = send(HttpRequest.newBuilder(uri("/api/v1/auth/csrf")).GET());
        assertThat(csrf.statusCode()).isEqualTo(204);
        String csrfCookie = setCookie(csrf, SecurityConfig.CSRF_COOKIE).orElseThrow();
        assertThat(csrfCookie).containsIgnoringCase("SameSite=Lax").contains("Path=/").contains("Secure");
        assertThat(csrfCookie).doesNotContainIgnoringCase("HttpOnly"); // the web app must read it
        String csrfToken = cookieValue(csrfCookie);

        // 2. Login without the header is refused, with it accepted
        String body = AuthTestSupport.loginJson(IntegrationTest.ADMIN_LOGIN, IntegrationTest.ADMIN_PASSWORD);
        HttpResponse<String> withoutHeader = send(json("/api/v1/auth/login", body)
                .header("Cookie", SecurityConfig.CSRF_COOKIE + "=" + csrfToken));
        assertThat(withoutHeader.statusCode()).isEqualTo(403);

        HttpResponse<String> login = send(json("/api/v1/auth/login", body)
                .header("Cookie", SecurityConfig.CSRF_COOKIE + "=" + csrfToken)
                .header(SecurityConfig.CSRF_HEADER, csrfToken));
        assertThat(login.statusCode()).isEqualTo(200);

        // 3. The session cookie is HTTP-only, SameSite=Lax and Secure
        String sessionCookie = setCookie(login, SecurityConfig.SESSION_COOKIE).orElseThrow();
        assertThat(sessionCookie)
                .containsIgnoringCase("HttpOnly")
                .containsIgnoringCase("SameSite=Lax")
                .contains("Secure");

        // 4. The session cookie identifies the user
        HttpResponse<String> me = send(HttpRequest.newBuilder(uri("/api/v1/auth/me"))
                .header("Cookie", SecurityConfig.SESSION_COOKIE + "=" + cookieValue(sessionCookie))
                .GET());
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.body()).contains("\"role\":\"ADMIN\"");
    }

    @Test
    void loginGivesANewSessionId() throws Exception {
        // Session fixation: a session ID that existed before the login (for example one an attacker
        // planted in the victim's browser) must not become the signed-in session.
        // Over real HTTP on purpose: Spring changes the ID only for a session the browser sent back.
        String csrfToken = cookieValue(setCookie(
                        send(HttpRequest.newBuilder(uri("/api/v1/auth/csrf")).GET()), SecurityConfig.CSRF_COOKIE)
                .orElseThrow());
        String body = AuthTestSupport.loginJson(IntegrationTest.ADMIN_LOGIN, IntegrationTest.ADMIN_PASSWORD);
        String csrfCookie = SecurityConfig.CSRF_COOKIE + "=" + csrfToken;

        HttpResponse<String> first = send(json("/api/v1/auth/login", body)
                .header("Cookie", csrfCookie)
                .header(SecurityConfig.CSRF_HEADER, csrfToken));
        String existingSession = cookieValue(setCookie(first, SecurityConfig.SESSION_COOKIE).orElseThrow());
        String newCsrfToken = cookieValue(setCookie(first, SecurityConfig.CSRF_COOKIE).orElseThrow());

        HttpResponse<String> second = send(json("/api/v1/auth/login", body)
                .header("Cookie", SecurityConfig.SESSION_COOKIE + "=" + existingSession + "; "
                        + SecurityConfig.CSRF_COOKIE + "=" + newCsrfToken)
                .header(SecurityConfig.CSRF_HEADER, newCsrfToken));

        assertThat(second.statusCode()).isEqualTo(200);
        String sessionAfter = cookieValue(setCookie(second, SecurityConfig.SESSION_COOKIE).orElseThrow());
        assertThat(sessionAfter).isNotEqualTo(existingSession);
    }

    @Test
    void sessionTimeoutDefaultsTo8Hours() {
        // Configurable through LTP_SESSION_TIMEOUT
        assertThat(environment.getProperty("server.servlet.session.timeout", Duration.class))
                .isEqualTo(Duration.ofHours(8));
    }

    HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpRequest.Builder json(String path, String body) {
        return HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
    }

    URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    /** The last Set-Cookie header for this cookie (an earlier one may delete it first). */
    static Optional<String> setCookie(HttpResponse<?> response, String name) {
        List<String> cookies = response.headers().allValues("Set-Cookie").stream()
                .filter(header -> header.startsWith(name + "="))
                .toList();
        return cookies.isEmpty() ? Optional.empty() : Optional.of(cookies.getLast());
    }

    static String cookieValue(String setCookieHeader) {
        String pair = setCookieHeader.split(";", 2)[0];
        return pair.substring(pair.indexOf('=') + 1);
    }
}
