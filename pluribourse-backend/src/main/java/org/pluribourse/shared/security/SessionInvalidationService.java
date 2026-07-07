package org.pluribourse.shared.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class SessionInvalidationService {

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    /**
     * Revokes every currently open session for the given principal name (e.g. all devices/tabs
     * a volunteer is logged in on), not just one. Best-effort: failures are logged and swallowed
     * rather than propagated, so a session-store hiccup never rolls back the caller's own transaction.
     */
    public void invalidateSessionsFor(String username) {
        try {
            sessionRepository.findByPrincipalName(username)
                    .keySet()
                    .forEach(this::deleteSessionQuietly);
        } catch (RuntimeException e) {
            log.warn("Failed to look up sessions to revoke", e);
        }
    }

    private void deleteSessionQuietly(String sessionId) {
        try {
            sessionRepository.deleteById(sessionId);
        } catch (RuntimeException e) {
            log.warn("Failed to revoke a session", e);
        }
    }
}
