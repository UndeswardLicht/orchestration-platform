package dev.ltp.control.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * A platform user (US-23). Accounts are soft-deleted: {@code deletedAt} is set instead of removing the
 * row, so the scenarios and runs the user created keep a valid reference.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String login;

    @Column(length = 254)
    private String email;

    /** Always a {@code DelegatingPasswordEncoder} value such as {@code {bcrypt}...} (NFR-05). */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected User() {}

    public User(String login, String email, String passwordHash, Role role) {
        this.login = Objects.requireNonNull(login);
        this.email = email;
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.role = Objects.requireNonNull(role);
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getLogin() {
        return login;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** Role changes go through {@link UserService#changeRole}, which also ends the user's sessions. */
    void changeRole(Role newRole) {
        this.role = Objects.requireNonNull(newRole);
    }
}
