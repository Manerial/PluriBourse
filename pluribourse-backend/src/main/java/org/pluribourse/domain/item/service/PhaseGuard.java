package org.pluribourse.domain.item.service;

import org.pluribourse.domain.edition.entity.*;
import org.pluribourse.domain.item.exception.*;

/**
 * Items and lots can only be created, modified or deleted while the edition is in the
 * Deposit phase (FR-024). Shared between ItemService, LotService, PosScanService and
 * SettlementService so the phase rules and their error types are defined once.
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

    /**
     * POS scanning (story 4.1) is only allowed while the edition is in the Sale phase — a
     * server-side mirror of the frontend's sale-phase route guard, since the client is never
     * trusted alone.
     */
    public static void requireSalePhase(Edition edition) {
        if (edition.getPhase() != PhaseType.SALE) {
            throw new SalePhaseRequiredException();
        }
    }

    /**
     * Settlement (story 5.1) is only reachable while the edition is in Post-vente — a
     * server-side mirror of the frontend's settlementPhaseGuard, since the client is never
     * trusted alone (same rationale as requireSalePhase).
     */
    public static void requirePostSalePhase(Edition edition) {
        if (edition.getPhase() != PhaseType.POST_SALE) {
            throw new SettlementNotAllowedException();
        }
    }

    /**
     * The edition-wide summary report (story 5.4, FR-055) is meaningful only once the Sale
     * phase has ended — reachable in Post-vente and Clôturée alike (EXPERIENCE.md treats both
     * as the same accessibility class for /admin/reports).
     */
    public static void requirePostSaleOrClosedPhase(Edition edition) {
        if (edition.getPhase() != PhaseType.POST_SALE && edition.getPhase() != PhaseType.CLOSED) {
            throw new EditionReportNotAllowedException();
        }
    }
}
