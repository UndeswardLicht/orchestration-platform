package dev.ltp.control.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

    /** The active (not deleted) user with this login, ignoring case, as the unique index does. */
    @Query("select u from User u where lower(u.login) = lower(:login) and u.deletedAt is null")
    Optional<User> findActiveByLogin(String login);

    boolean existsByRoleAndDeletedAtIsNull(Role role);

    long countByRoleAndDeletedAtIsNull(Role role);
}
