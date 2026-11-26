package dev.ltp.control;

import static dev.ltp.control.BrowserCsrf.browserCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * NFR-05 "100 % of API calls authenticated": every operation in the OpenAPI document answers 401
 * without sign-in, except the explicit allow-list. New endpoints are covered automatically.
 */
@IntegrationTest
class AllEndpointsRequireSignInTests {

    /** Operations that must work before signing in. */
    static final Set<String> ALLOW_LIST =
            Set.of("GET /api/v1/auth/csrf", "POST /api/v1/auth/login", "POST /api/v1/auth/logout");

    static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "patch", "delete");

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper jsonMapper;

    @Test
    void everyOperationExceptTheAllowListAnswers401WithoutSignIn() throws Exception {
        String spec = mvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();
        JsonNode paths = jsonMapper.readTree(spec).path("paths");

        List<String> checked = new ArrayList<>();
        List<String> open = new ArrayList<>();
        for (Map.Entry<String, JsonNode> path : paths.properties()) {
            for (Map.Entry<String, JsonNode> operation : path.getValue().properties()) {
                if (!HTTP_METHODS.contains(operation.getKey())) {
                    continue;
                }
                String name = operation.getKey().toUpperCase() + " " + path.getKey();
                if (ALLOW_LIST.contains(name)) {
                    continue;
                }
                String url = path.getKey().replaceAll("\\{[^}]+}", "1");
                // A valid CSRF token, so the answer shows the sign-in check, not the CSRF check
                int status = mvc.perform(request(HttpMethod.valueOf(operation.getKey().toUpperCase()), url)
                                .with(browserCsrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                        .andReturn()
                        .getResponse()
                        .getStatus();
                checked.add(name);
                if (status != 401) {
                    open.add(name + " -> " + status);
                }
            }
        }

        assertThat(checked).as("operations found in the OpenAPI document").contains("GET /api/v1/users");
        assertThat(open).as("operations reachable without sign-in").isEmpty();
    }
}
