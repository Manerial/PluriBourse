package org.pluribourse.domain.pos.service;

import lombok.RequiredArgsConstructor;
import org.pluribourse.domain.edition.entity.PhaseType;
import org.pluribourse.domain.item.repository.LotRepository;
import org.pluribourse.domain.pos.entity.Basket;
import org.pluribourse.domain.pos.repository.BasketRepository;
import org.pluribourse.shared.sse.BasketCancelledEventDto;
import org.pluribourse.shared.sse.SseEmitterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.List;

/**
 * The single POS basket-cancellation routine, shared by the two triggers that discard an active
 * basket: an edition phase change (FR-090, {@code EditionService}) and an explicit volunteer logout
 * (FR-110, {@code BasketCancellingLogoutHandler}). Both release every lot reservation the basket
 * held ({@link LotRepository#releaseAllByBasketId}, with the {@code fk_lots_reserved_basket}
 * {@code ON DELETE SET NULL} as the DB-level safety net) and delete the {@code Basket} rows, whose
 * {@code basket_items} follow by FK cascade.
 * <p>
 * Lives in {@code domain.pos} rather than {@code domain.edition} because it is basket-domain logic
 * and because {@code EditionService} cannot host it: {@code PosBasketService} already depends on
 * {@code EditionService}, so the reverse dependency would be a cycle. This service depends only on
 * repositories and the SSE registry.
 * <p>
 * The phase-change path ({@link #cancelBaskets}) broadcasts one {@code basket-cancelled} after
 * commit — a broadcast to every connected terminal is correct there, since every basket really is
 * being cancelled. The logout path ({@link #cancelBasketSilently}) emits nothing: {@code basket-
 * cancelled} is an untargeted {@link SseEmitterRegistry#broadcast} and {@link BasketCancelledEventDto}
 * carries no user identity, so emitting it on one volunteer's logout would wipe every other
 * cashier's in-progress basket. The logging-out user's other tabs share the now-invalidated session
 * and drop to the login screen on their next request (story 4.8 Dev Notes "Notification de
 * déconnexion — pas de SSE").
 */
@Service
@RequiredArgsConstructor
public class BasketCancellationService {

    private final BasketRepository basketRepository;
    private final LotRepository lotRepository;
    private final SseEmitterRegistry sseEmitterRegistry;

    /**
     * Cancels every basket in {@code baskets} and defers a single {@code basket-cancelled} broadcast
     * (carrying {@code phaseForEvent}) to after the surrounding transaction commits. No-op on an
     * empty collection. Used only by the phase-change trigger (FR-090).
     */
    @Transactional
    public void cancelBaskets(Collection<Basket> baskets, PhaseType phaseForEvent) {
        if (baskets.isEmpty()) {
            return;
        }
        Long editionId = baskets.iterator().next().getEdition().getId();
        releaseAndDelete(baskets);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sseEmitterRegistry.broadcast("basket-cancelled", new BasketCancelledEventDto(editionId, phaseForEvent));
            }
        });
    }

    /**
     * Cancels a single basket with the same releases and deletes as {@link #cancelBaskets}, but
     * emits no event at all. Used only by the logout trigger (FR-110) — see the class Javadoc for
     * why the logout path must stay silent. The {@code basket} may be detached (the logout filter
     * opens no transaction); this {@code @Transactional} method merges it on delete.
     */
    @Transactional
    public void cancelBasketSilently(Basket basket) {
        releaseAndDelete(List.of(basket));
    }

    private void releaseAndDelete(Collection<Basket> baskets) {
        baskets.forEach(basket -> lotRepository.releaseAllByBasketId(basket.getId()));
        basketRepository.deleteAll(baskets);
    }
}
