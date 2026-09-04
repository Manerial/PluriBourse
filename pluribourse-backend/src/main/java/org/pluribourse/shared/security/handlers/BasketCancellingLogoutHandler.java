package org.pluribourse.shared.security.handlers;

import jakarta.servlet.http.*;
import lombok.*;
import lombok.extern.slf4j.*;
import org.pluribourse.domain.edition.entity.*;
import org.pluribourse.domain.edition.repository.*;
import org.pluribourse.domain.pos.repository.*;
import org.pluribourse.domain.pos.service.*;
import org.pluribourse.domain.user.entities.*;
import org.springframework.security.core.*;
import org.springframework.security.web.authentication.logout.*;
import org.springframework.stereotype.*;

/**
 * FR-110 (SCP 2026-09-03) — on an explicit {@code POST /auth/logout}, discards the volunteer's
 * active POS basket for the active edition and releases its lot reservations, so a shared
 * workstation left after logout keeps no stale basket or orphan reservation. Best-effort: any
 * failure is logged (no personal data) and swallowed so it never turns a logout into an error.
 * <p>
 * Reads the {@link Authentication} passed by {@code LogoutFilter} (captured before any handler
 * runs), never {@code SecurityContextHolder} — so its ordering against
 * {@code SecurityContextLogoutHandler} / {@code invalidateHttpSession} is irrelevant, the work here
 * is purely DB-side. A {@code Basket} only exists during the Sale phase; in the two other active
 * phases {@code findByEditionIdAndUserId} is empty and this is a no-op. Cancellation goes through
 * {@link BasketCancellationService#cancelBasketSilently} — the logout path must emit no
 * {@code basket-cancelled} broadcast (it would wipe every other cashier's basket).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BasketCancellingLogoutHandler implements LogoutHandler {

    private final EditionRepository editionRepository;
    private final BasketRepository basketRepository;
    private final BasketCancellationService basketCancellationService;

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof PluriBourseUserDetails principal)) {
            return;
        }
        try {
            editionRepository.findFirstByPhaseIn(PhaseType.ACTIVE).ifPresent(edition ->
                    basketRepository.findByEditionIdAndUserId(edition.getId(), principal.getUserId())
                            .ifPresent(basketCancellationService::cancelBasketSilently));
        } catch (RuntimeException e) {
            log.warn("Failed to cancel active POS basket on logout", e);
        }
    }
}
