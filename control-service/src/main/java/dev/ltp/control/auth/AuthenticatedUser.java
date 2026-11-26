package dev.ltp.control.auth;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The signed-in user as kept in the session. Two instances are equal when they belong to the same user
 * id, which the session registry relies on to find all sessions of one user. The password hash is
 * erased right after login, so it is never kept in the session.
 */
public final class AuthenticatedUser implements UserDetails, CredentialsContainer {

    private final long id;
    private final String login;
    private final Role role;
    private String passwordHash;

    AuthenticatedUser(long id, String login, Role role, String passwordHash) {
        this.id = id;
        this.login = Objects.requireNonNull(login);
        this.role = Objects.requireNonNull(role);
        this.passwordHash = passwordHash;
    }

    static AuthenticatedUser of(User user) {
        return new AuthenticatedUser(user.getId(), user.getLogin(), user.getRole(), user.getPasswordHash());
    }

    public long id() {
        return id;
    }

    public String login() {
        return login;
    }

    public Role role() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return login;
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AuthenticatedUser user && user.id == id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "AuthenticatedUser[id=" + id + ", login=" + login + ", role=" + role + "]";
    }
}
