package dev.ltp.control.auth;

import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first admin account on start if no active admin exists (US-23, T-03).
 *
 * <p>It never changes an existing account: once an admin exists, changing {@code LTP_ADMIN_PASSWORD}
 * has no effect. The account is created in code rather than in a Flyway migration so that no password
 * hash is kept in Git and every installation gets its own password.
 */
@Component
@EnableConfigurationProperties(AdminProperties.class)
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    /** Passwords from the examples and Compose defaults; accepted, but with a warning. */
    private static final Set<String> PLACEHOLDERS = Set.of("admin", "change-me", "changeme", "password");

    static final int RECOMMENDED_MIN_LENGTH = 12;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties properties;

    public AdminSeeder(UserRepository users, PasswordEncoder passwordEncoder, AdminProperties properties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed();
    }

    /** Creates the admin if needed. Returns {@code true} if an account was created. */
    @Transactional
    public boolean seed() {
        if (users.existsByRoleAndDeletedAtIsNull(Role.ADMIN)) {
            log.debug("An admin account exists; nothing to seed");
            return false;
        }
        String login = properties.login() == null ? "" : properties.login().strip();
        String password = properties.password() == null ? "" : properties.password();
        if (login.isEmpty() || password.isBlank()) {
            throw new IllegalStateException("No admin account exists. Set LTP_ADMIN_LOGIN and LTP_ADMIN_PASSWORD"
                    + " (deploy/.env) so the first admin can be created.");
        }
        if (users.findActiveByLogin(login).isPresent()) {
            throw new IllegalStateException("No admin account exists, but the login '" + login
                    + "' belongs to a non-admin user. Choose another LTP_ADMIN_LOGIN.");
        }
        if (password.length() < RECOMMENDED_MIN_LENGTH
                || PLACEHOLDERS.contains(password.toLowerCase(Locale.ROOT))) {
            log.warn("The admin password is a placeholder or shorter than {} characters."
                    + " Change it before exposing the platform.", RECOMMENDED_MIN_LENGTH);
        }
        users.save(new User(login, null, passwordEncoder.encode(password), Role.ADMIN));
        log.info("Created the first admin account '{}'", login);
        return true;
    }
}
