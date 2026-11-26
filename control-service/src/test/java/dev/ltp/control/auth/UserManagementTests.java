package dev.ltp.control.auth;

import static dev.ltp.control.BrowserCsrf.browserCsrf;
import static dev.ltp.control.auth.AuthTestSupport.PASSWORD;
import static dev.ltp.control.auth.AuthTestSupport.TEST_LOGIN_PREFIX;
import static dev.ltp.control.auth.AuthTestSupport.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ltp.control.IntegrationTest;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * User management and role checks (US-23). Not transactional on purpose: ending sessions happens after
 * the commit. Users created here are deleted after each test.
 */
@IntegrationTest
class UserManagementTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    UserService userService;

    @Autowired
    UserRepository users;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    RoleHierarchy roleHierarchy;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void deleteTestUsers() {
        jdbc.update("delete from users where login like ?", TEST_LOGIN_PREFIX + "%");
    }

    // --- US-23 acceptance criterion on a real endpoint ---------------------------------------------

    @Test
    void viewerAndDeveloperCannotCreateUsersAndNothingIsCreated() throws Exception {
        for (Role role : new Role[] {Role.VIEWER, Role.DEVELOPER}) {
            MockHttpSession session = signInAs(role);

            createUser(session, TEST_LOGIN_PREFIX + "victim", PASSWORD, "ADMIN")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.detail").value("Your role does not allow this action."));
        }
        assertThat(users.findActiveByLogin(TEST_LOGIN_PREFIX + "victim")).isEmpty();
    }

    @Test
    void anonymousCallerGets401NotForbidden() throws Exception {
        mvc.perform(post("/api/v1/users")
                        .with(browserCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson(TEST_LOGIN_PREFIX + "x", PASSWORD, "VIEWER")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminCreatesAUserWhoCanSignInWithThatRole() throws Exception {
        MockHttpSession admin = signInAsAdmin();

        createUser(admin, TEST_LOGIN_PREFIX + "dana", PASSWORD, "DEVELOPER")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", Matchers.matchesPattern("/api/v1/users/\\d+")))
                .andExpect(jsonPath("$.login").value(TEST_LOGIN_PREFIX + "dana"))
                .andExpect(jsonPath("$.role").value("DEVELOPER"))
                .andExpect(jsonPath("$.password").doesNotExist());

        User stored = users.findActiveByLogin(TEST_LOGIN_PREFIX + "dana").orElseThrow();
        assertThat(stored.getPasswordHash()).startsWith("{bcrypt}");
        MockHttpSession dana = login(mvc, TEST_LOGIN_PREFIX + "dana", PASSWORD);
        mvc.perform(get("/api/v1/auth/me").session(dana)).andExpect(jsonPath("$.role").value("DEVELOPER"));
    }

    @Test
    void onlyAdminsListUsers() throws Exception {
        mvc.perform(get("/api/v1/users").session(signInAsAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].login", Matchers.hasItem(IntegrationTest.ADMIN_LOGIN)))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
        mvc.perform(get("/api/v1/users").session(signInAs(Role.DEVELOPER))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users").session(signInAs(Role.VIEWER))).andExpect(status().isForbidden());
    }

    @Test
    void viewerMayReadButNotChange() throws Exception {
        MockHttpSession viewer = signInAs(Role.VIEWER);

        mvc.perform(get("/api/v1/auth/me").session(viewer)).andExpect(status().isOk());
        // No change endpoint is open to Viewers, even one without its own role rule
        mvc.perform(post("/api/v1/scenarios").session(viewer).with(browserCsrf())).andExpect(status().isForbidden());
    }

    @Test
    void roleLadderAdminIncludesDeveloperIncludesViewer() {
        assertThat(reachable("ROLE_ADMIN")).contains("ROLE_ADMIN", "ROLE_DEVELOPER", "ROLE_VIEWER");
        assertThat(reachable("ROLE_DEVELOPER")).contains("ROLE_DEVELOPER", "ROLE_VIEWER").doesNotContain("ROLE_ADMIN");
        assertThat(reachable("ROLE_VIEWER")).containsExactly("ROLE_VIEWER");
    }

    // --- input checks -------------------------------------------------------------------------------

    @Test
    void duplicateLoginIgnoringCaseIs409() throws Exception {
        MockHttpSession admin = signInAsAdmin();
        createUser(admin, TEST_LOGIN_PREFIX + "dana", PASSWORD, "DEVELOPER").andExpect(status().isCreated());

        createUser(admin, "IT-DANA", PASSWORD, "VIEWER").andExpect(status().isConflict());
    }

    @Test
    void invalidFieldsAre400NamingTheField() throws Exception {
        MockHttpSession admin = signInAsAdmin();

        createUser(admin, TEST_LOGIN_PREFIX + "short", "too-short", "VIEWER")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));
        // 37 two-byte characters: within 72 characters but 74 bytes, which bcrypt would cut silently
        createUser(admin, TEST_LOGIN_PREFIX + "bytes", "é".repeat(37), "VIEWER")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));
        createUser(admin, "it bad login", PASSWORD, "VIEWER")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("login"));
        createUser(admin, TEST_LOGIN_PREFIX + "role", PASSWORD, "SUPERUSER").andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/users")
                        .session(admin)
                        .with(browserCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\": \"it-mail\", \"email\": \"not-an-email\", \"password\": \"" + PASSWORD
                                + "\", \"role\": \"VIEWER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("email"));
    }

    // --- role changes -------------------------------------------------------------------------------

    @Test
    void roleChangeEndsTheUsersSessionsAndAppliesOnNextSignIn() throws Exception {
        User dana = userService.create(TEST_LOGIN_PREFIX + "dana", null, PASSWORD, Role.DEVELOPER);
        MockHttpSession danaSession = login(mvc, dana.getLogin(), PASSWORD);
        mvc.perform(get("/api/v1/auth/me").session(danaSession)).andExpect(status().isOk());

        changeRole(signInAsAdmin(), dana.getId(), "VIEWER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("VIEWER"));

        mvc.perform(get("/api/v1/auth/me").session(danaSession))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Your access has changed. Sign in again."));
        MockHttpSession again = login(mvc, dana.getLogin(), PASSWORD);
        mvc.perform(get("/api/v1/auth/me").session(again)).andExpect(jsonPath("$.role").value("VIEWER"));
    }

    @Test
    void roleChangeDoesNotEndOtherUsersSessions() throws Exception {
        User dana = userService.create(TEST_LOGIN_PREFIX + "dana", null, PASSWORD, Role.DEVELOPER);
        MockHttpSession marek = signInAs(Role.DEVELOPER);

        changeRole(signInAsAdmin(), dana.getId(), "VIEWER").andExpect(status().isOk());

        mvc.perform(get("/api/v1/auth/me").session(marek)).andExpect(status().isOk());
    }

    @Test
    void theLastAdminCannotLoseTheRole() throws Exception {
        long adminId = users.findActiveByLogin(IntegrationTest.ADMIN_LOGIN).orElseThrow().getId();

        changeRole(signInAsAdmin(), adminId, "DEVELOPER")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(Matchers.containsString("last admin")));
        assertThat(users.findById(adminId).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void anAdminCanBeDemotedWhileAnotherAdminExists() throws Exception {
        User second = userService.create(TEST_LOGIN_PREFIX + "second-admin", null, PASSWORD, Role.ADMIN);

        changeRole(signInAsAdmin(), second.getId(), "DEVELOPER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("DEVELOPER"));
    }

    @Test
    void developerCannotChangeRoles() throws Exception {
        User dana = userService.create(TEST_LOGIN_PREFIX + "dana", null, PASSWORD, Role.VIEWER);

        changeRole(signInAs(Role.DEVELOPER), dana.getId(), "ADMIN").andExpect(status().isForbidden());
        assertThat(users.findById(dana.getId()).orElseThrow().getRole()).isEqualTo(Role.VIEWER);
    }

    @Test
    void unknownOrDeletedUserIs404() throws Exception {
        User gone = userService.create(TEST_LOGIN_PREFIX + "gone", null, PASSWORD, Role.VIEWER);
        jdbc.update("update users set deleted_at = now() where id = ?", gone.getId());
        MockHttpSession admin = signInAsAdmin();

        changeRole(admin, gone.getId(), "DEVELOPER").andExpect(status().isNotFound());
        changeRole(admin, 999_999, "DEVELOPER").andExpect(status().isNotFound());
    }

    // --- helpers ------------------------------------------------------------------------------------

    MockHttpSession signInAsAdmin() throws Exception {
        return login(mvc, IntegrationTest.ADMIN_LOGIN, IntegrationTest.ADMIN_PASSWORD);
    }

    MockHttpSession signInAs(Role role) throws Exception {
        String login = TEST_LOGIN_PREFIX + role.name().toLowerCase() + "-" + System.nanoTime();
        userService.create(login, null, PASSWORD, role);
        return login(mvc, login, PASSWORD);
    }

    ResultActions createUser(MockHttpSession session, String login, String password, String role) throws Exception {
        return mvc.perform(post("/api/v1/users")
                .session(session)
                .with(browserCsrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(userJson(login, password, role)));
    }

    ResultActions changeRole(MockHttpSession session, long id, String role) throws Exception {
        return mvc.perform(patch("/api/v1/users/" + id)
                .session(session)
                .with(browserCsrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\": \"" + role + "\"}"));
    }

    static String userJson(String login, String password, String role) {
        return "{\"login\": \"" + login + "\", \"password\": \"" + password + "\", \"role\": \"" + role + "\"}";
    }

    List<String> reachable(String role) {
        return roleHierarchy.getReachableGrantedAuthorities(AuthorityUtils.createAuthorityList(role)).stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }
}
