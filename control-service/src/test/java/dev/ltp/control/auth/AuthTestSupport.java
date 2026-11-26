package dev.ltp.control.auth;

import static dev.ltp.control.BrowserCsrf.browserCsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Signs in through the real login endpoint and returns the session for later requests. */
public final class AuthTestSupport {

    /** Logins of users created by tests start with this; they are deleted after each test. */
    public static final String TEST_LOGIN_PREFIX = "it-";

    public static final String PASSWORD = "a-long-test-password";

    private AuthTestSupport() {}

    public static MockHttpSession login(MockMvc mvc, String login, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                        .with(browserCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(login, password)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    static String loginJson(String login, String password) {
        return "{\"login\": \"" + login + "\", \"password\": \"" + password + "\"}";
    }
}
