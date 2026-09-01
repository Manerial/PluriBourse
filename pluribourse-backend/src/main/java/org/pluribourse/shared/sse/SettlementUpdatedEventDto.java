package org.pluribourse.shared.sse;

/**
 * Broadcast after a settle/markUnclaimed commits so the settlement screens open on the other
 * terminals refresh their list (ARCH-017, story 5.7). Sibling of {@link PhaseChangedEventDto} /
 * {@link BasketCancelledEventDto} — kept in this package for the same reason they are, not in
 * {@code domain.payout.dto}. Carries only IDs, never a {@code SellerProfile}, so no personal
 * data reaches the SSE stream or the logs.
 */
public record SettlementUpdatedEventDto(Long editionId, Long sellerId) {
}
