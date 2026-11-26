package dev.ltp.control.auth;

import static dev.ltp.control.BrowserCsrf.browserCsrf;
import static dev.ltp.control.auth.AuthTestSupport.PASSWORD;
import static dev.ltp.control.auth.AuthTestSupport.TEST_LOGIN_PREFIX;
import static dev.ltp.control.auth.AuthTestSupport.login;
import static dev.ltp.control.auth.AuthTestSupport.loginJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ltp.control.BrowserCsrf;
import dev.ltp.control.IntegrationTest;
import dev.ltp.control.config.SecurityConfig;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Sign-in, sign-out, "who am I" and the CSRF rules (US-23). */
@IntegrationTest
class AuthControllerTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    UserService userService;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void deleteTestUsers() {
        jdbc.update("delete from users where login like ?", TEST_LOGIN_PREFIX + "%");
    }

    @Test
    void adminSignsInAndSeesOwnRole() throws Exception {
        MockHttpSession session = login(mvc, IntegrationTest.ADMIN_LOGIN, IntegrationTest.ADMIN_PASSWORD);

        mvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value(IntegrationTest.ADMIN_LOGIN))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    void loginIgnoresCaseAndSurroundingSpaces() throws Exception {
        userService.create(TEST_LOGIN_PREFIX + "dana", null, PASSWORD, Role.DEVELOPER);

        mvc.perform(post("/api/v1/auth/login")
                        .with(browserCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("  IT-Dana ", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value(TEST_LOGIN_PREFIX + "dana"))
                .andExpect(jsonPath("$.role").value("DEVELOPER"));
    }

    @Test
    void wrongPasswordUnknownLoginAndDeletedUserGetTheSameAnswer() throws Exception {
        User deleted = userService.create(TEST_LOGIN_PREFIX + "gone", null, PASSWORD, Role.VIEWER);
        jdbc.update("update users set deleted_at = now() where id = ?", deleted.getId());

        for (String[] attempt : new String[][] {
            {IntegrationTest.ADMIN_LOGIN, "wrong-password-123"},
            {"nobody-with-this-login", "wrong-password-123"},
            {TEST_LOGIN_PREFIX + "gone", PASSWORD}
        }) {
            mvc.perform(post("/api/v1/auth/login")
                            .with(browserCsrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson(attempt[0], attempt[1])))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.detail").value("Invalid login or password"))
                    .andExpect(cookie().doesNotExist(SecurityConfig.SESSION_COOKIE));
        }
    }

    @Test
    void emptyLoginOrPasswordIsA400NamingTheField() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .with(browserCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("admin", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    @Test
    void loginWithoutCsrfTokenIsRefused() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(IntegrationTest.ADMIN_LOGIN, IntegrationTest.ADMIN_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("CSRF")));
    }

    @Test
    void changeWithoutCsrfTokenIsRefusedEvenWhenSignedIn() throws Exception {
        MockHttpSession session = login(mvc, IntegrationTest.ADMIN_LOGIN, IntegrationTest.ADMIN_PASSWORD);

        mvc.perform(post("/api/v1/users")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\": \"it-x\", \"password\": \"" + PASSWORD + "\", \"role\": \"VIEWER\"}"))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select count(*) from users where login = 'it-x'", Integer.class))
                .isZero();
    }

    @Test
    void loginIssuesANewCsrfToken() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                        .with(browserCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(IntegrationTest.ADMIN_LOGIN, IntegrationTest.ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        // The old token is deleted first, then a new one is set; the browser keeps the last cookie
        List<Cookie> csrfCookies = Arrays.stream(result.getResponse().getCookies())
                .filter(cookie -> cookie.getName().equals(SecurityConfig.CSRF_COOKIE))
                .toList();
        assertThat(csrfCookies).isNotEmpty();
        assertThat(csrfCookies.getLast().getMaxAge()).isNotZero();
        assertThat(csrfCookies.getLast().getValue()).isNotBlank().isNotEqualTo(BrowserCsrf.TOKEN);
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        MockHttpSession session = login(mvc, IntegrationTest.ADMIN_LOGIN, IntegrationTest.ADMIN_PASSWORD);

        mvc.perform(post("/api/v1/auth/logout").session(session).with(browserCsrf()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(SecurityConfig.SESSION_COOKIE, 0));

        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWhenNotSignedInIsFine() throws Exception {
        mvc.perform(post("/api/v1/auth/logout").with(browserCsrf())).andExpect(status().isNoContent());
    }

    @Test
    void meWithoutSessionIs401WithAProblemBody() throws Exception {
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Sign in first."));
    }

    @Test
    void noBrowserLoginPopup() throws Exception {
        // HTTP Basic is off, so browsers do not show their own login dialog
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(result -> assertThat(result.getResponse().getHeader("WWW-Authenticate")).isNull());
    }

    @Test
    void csrfEndpointSetsTheCookie() throws Exception {
        mvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists(SecurityConfig.CSRF_COOKIE))
                .andExpect(cookie().httpOnly(SecurityConfig.CSRF_COOKIE, false));
    }
}
