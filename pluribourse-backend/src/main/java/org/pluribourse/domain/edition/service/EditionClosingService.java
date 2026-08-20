package org.pluribourse.domain.edition.service;

import lombok.RequiredArgsConstructor;
import org.pluribourse.domain.edition.dto.EditionDto;
import org.pluribourse.domain.edition.entity.Edition;
import org.pluribourse.domain.edition.entity.PhaseType;
import org.pluribourse.domain.edition.exception.PhaseAlreadyClosedException;
import org.pluribourse.domain.item.service.PhaseGuard;
import org.pluribourse.domain.payout.service.SettlementService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Edition closure (story 2.7, FR-096): a new orchestrating service, not new methods on
 * {@link EditionService} — {@link SettlementService} already depends on {@code EditionService},
 * so adding the reverse dependency there would create a circular Spring bean dependency.
 */
@Service
@RequiredArgsConstructor
public class EditionClosingService {

    private final EditionService editionService;
    private final SettlementService settlementService;

    /**
     * Guard order matters: {@link PhaseGuard#requirePostSalePhase} throws for any phase other than
     * Post-vente, including Clôturée — checked alone, closing an already-closed edition would never
     * reach {@link EditionService#advancePhase} and never surface {@link PhaseAlreadyClosedException}.
     * The explicit CLOSED check runs first so that case stays correctly typed. Runs inside one
     * transaction (default {@code REQUIRED} propagation joins {@code advancePhase}'s own
     * {@code @Transactional}) — this is what makes AC 3 atomic.
     */
    @Transactional
    public EditionDto closeEdition(Long id) {
        Edition edition = editionService.requireEdition(id);
        if (edition.getPhase() == PhaseType.CLOSED) {
            throw new PhaseAlreadyClosedException();
        }
        PhaseGuard.requirePostSalePhase(edition);
        settlementService.closeAllUnsettledAsUnclaimed(edition);
        return editionService.advancePhase(id);
    }
}
