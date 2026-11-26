package dev.ltp.control.auth;

import dev.ltp.control.config.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** User management for admins (US-23): list, create, change role. */
@Service
public class UserService {

    /** Same minimum the admin seeder recommends. */
    public static final int MIN_PASSWORD_LENGTH = 12;

    /** bcrypt uses only the first 72 bytes of a password; longer ones are refused, not cut silently. */
    public static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder, ApplicationEventPublisher events) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<User> listActive() {
        return users.findByDeletedAtIsNullOrderByLoginAsc();
    }

    @Transactional
    public User create(String login, String email, String password, Role role) {
        String cleanLogin = login.strip();
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw ApiException.badRequest("password", "must be at most " + MAX_PASSWORD_BYTES + " bytes");
        }
        if (users.findActiveByLogin(cleanLogin).isPresent()) {
            throw ApiException.conflict("A user with the login '" + cleanLogin + "' already exists");
        }
        String cleanEmail = email == null || email.isBlank() ? null : email.strip();
        try {
            return users.saveAndFlush(new User(cleanLogin, cleanEmail, passwordEncoder.encode(password), role));
        } catch (DataIntegrityViolationException e) {
            // Another request created the same login in the meantime (unique index users_login_uq)
            throw ApiException.conflict("A user with the login '" + cleanLogin + "' already exists");
        }
    }

    /**
     * Changes the role and ends the user's sessions. The last active admin cannot lose the role, so
     * the platform always keeps an administrator. Admin rows are locked while checking, so two admins
     * demoting each other at the same time cannot both succeed.
     */
    @Transactional
    public User changeRole(long id, Role role) {
        User user = users.findById(id)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> ApiException.notFound("No active user with id " + id));
        if (user.getRole() == role) {
            return user;
        }
        if (user.getRole() == Role.ADMIN && users.lockActiveByRole(Role.ADMIN).size() <= 1) {
            throw ApiException.conflict("'" + user.getLogin() + "' is the last admin and must keep the ADMIN role");
        }
        user.changeRole(role);
        events.publishEvent(new UserAccessChanged(user.getId()));
        return user;
    }
}
