package dev.ltp.control.auth;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

    /** The active (not deleted) user with this login, ignoring case, as the unique index does. */
    @Query("select u from User u where lower(u.login) = lower(:login) and u.deletedAt is null")
    Optional<User> findActiveByLogin(String login);

    boolean existsByRoleAndDeletedAtIsNull(Role role);

    long countByRoleAndDeletedAtIsNull(Role role);

    List<User> findByDeletedAtIsNullOrderByLoginAsc();

    /** Active users with this role, locked until the end of the transaction (last-admin check). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.role = :role and u.deletedAt is null")
    List<User> lockActiveByRole(Role role);
}
