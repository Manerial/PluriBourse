package org.pluribourse.item.service;

import org.pluribourse.edition.entity.*;
import org.pluribourse.item.exception.*;

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
}
