package dev.ltp.control.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Ends all sessions of a user whose access changed, so new rights apply at once and not only after
 * the next sign-in. The next request of such a session gets 401 and the user signs in again.
 */
@Component
class UserSessionExpirer {

    private static final Logger log = LoggerFactory.getLogger(UserSessionExpirer.class);

    private final SessionRegistry sessionRegistry;

    UserSessionExpirer(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    /** After the commit only: a rolled-back change must not sign anybody out. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(UserAccessChanged event) {
        long ended = sessionRegistry.getAllPrincipals().stream()
                .filter(principal -> principal instanceof AuthenticatedUser user && user.id() == event.userId())
                .flatMap(principal -> sessionRegistry.getAllSessions(principal, false).stream())
                .peek(SessionInformation::expireNow)
                .count();
        log.info("Ended {} session(s) of user {} after an access change", ended, event.userId());
    }
}
