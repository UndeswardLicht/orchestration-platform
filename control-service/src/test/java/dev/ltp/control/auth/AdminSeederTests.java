package dev.ltp.control.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ltp.control.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/** The first admin account is created on start, once, and never overwritten (US-23, T-03). */
@IntegrationTest
@Transactional
class AdminSeederTests {

    @Autowired
    UserRepository users;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    AdminSeeder seeder;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void adminIsCreatedOnStartWithASaltedHash() {
        User admin = users.findActiveByLogin(IntegrationTest.ADMIN_LOGIN).orElseThrow();

        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.getPasswordHash()).startsWith("{bcrypt}").doesNotContain(IntegrationTest.ADMIN_PASSWORD);
        assertThat(passwordEncoder.matches(IntegrationTest.ADMIN_PASSWORD, admin.getPasswordHash())).isTrue();
    }

    @Test
    void runningAgainCreatesNoSecondAdmin() {
        assertThat(seeder.seed()).isFalse();
        assertThat(users.countByRoleAndDeletedAtIsNull(Role.ADMIN)).isEqualTo(1);
    }

    @Test
    void existingAdminPasswordIsNeverOverwritten() {
        String before = users.findActiveByLogin(IntegrationTest.ADMIN_LOGIN).orElseThrow().getPasswordHash();

        boolean created = seederWith(IntegrationTest.ADMIN_LOGIN, "a-completely-different-password").seed();

        assertThat(created).isFalse();
        assertThat(users.findActiveByLogin(IntegrationTest.ADMIN_LOGIN).orElseThrow().getPasswordHash())
                .isEqualTo(before);
    }

    @Test
    void createsAnAdminWhenNoActiveAdminExists() {
        deleteAllAdmins();

        assertThat(seederWith("root", "a-long-enough-password").seed()).isTrue();

        User root = users.findActiveByLogin("root").orElseThrow();
        assertThat(root.getRole()).isEqualTo(Role.ADMIN);
        assertThat(passwordEncoder.matches("a-long-enough-password", root.getPasswordHash())).isTrue();
    }

    @Test
    void emptyPasswordStopsTheStartWhenAnAdminIsNeeded() {
        deleteAllAdmins();

        assertThatThrownBy(() -> seederWith("root", "").seed())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LTP_ADMIN_PASSWORD");
        assertThatThrownBy(() -> seederWith("root", null).seed()).isInstanceOf(IllegalStateException.class);
        assertThat(users.countByRoleAndDeletedAtIsNull(Role.ADMIN)).isZero();
    }

    @Test
    void emptyPasswordIsFineOnceAnAdminExists() {
        assertThat(seederWith(IntegrationTest.ADMIN_LOGIN, "").seed()).isFalse();
    }

    @Test
    void loginTakenByANonAdminStopsTheStart() {
        deleteAllAdmins();
        users.save(new User("taken", null, passwordEncoder.encode("whatever-password"), Role.VIEWER));

        assertThatThrownBy(() -> seederWith("TAKEN", "a-long-enough-password").seed())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non-admin");
    }

    AdminSeeder seederWith(String login, String password) {
        return new AdminSeeder(users, passwordEncoder, new AdminProperties(login, password));
    }

    void deleteAllAdmins() {
        jdbc.update("update users set deleted_at = now() where role = 'ADMIN'");
    }
}
