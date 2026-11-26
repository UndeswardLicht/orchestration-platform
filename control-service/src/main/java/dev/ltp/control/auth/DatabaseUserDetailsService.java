package dev.ltp.control.auth;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Loads active (not deleted) users by login, ignoring case, for the login check (US-23). */
@Service
class DatabaseUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    DatabaseUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String login) {
        return users.findActiveByLogin(login)
                .map(AuthenticatedUser::of)
                .orElseThrow(() -> new UsernameNotFoundException("No active user with this login"));
    }
}
