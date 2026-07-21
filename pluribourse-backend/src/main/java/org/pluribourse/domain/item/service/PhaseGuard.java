package org.pluribourse.domain.item.service;

import org.pluribourse.domain.edition.entity.*;
import org.pluribourse.domain.item.exception.*;

/**
 * Items and lots can only be created, modified or deleted while the edition is in the
 * Deposit phase (FR-024). Shared between ItemService and LotService so the rule and its
 * error type (item-modification-locked) are defined once.
 */
public final class PhaseGuard {

    private PhaseGuard() {
    }

    public static void requireDepositPhase(Edition edition) {
        if (edition.getPhase() != PhaseType.DEPOSIT) {
            throw new ItemModificationNotAllowedException();
        }
    }

    /**
     * The seller file view (`/volunteer/deposit`) stays reachable through Post-vente so a
     * volunteer can reprint a deposit slip after the Deposit phase ends (story 3.6, FR-031).
     */
    public static void requireDepositOrPostSalePhase(Edition edition) {
        if (edition.getPhase() != PhaseType.DEPOSIT && edition.getPhase() != PhaseType.POST_SALE) {
            throw new DepositReprintNotAllowedException();
        }
    }
}
