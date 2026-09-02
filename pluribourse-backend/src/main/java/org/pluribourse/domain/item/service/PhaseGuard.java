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
     * Both deposit-page reprint actions — the thermal labels and the deposit slip PDF — are
     * restricted to the Deposit phase (story 5.8, tightening story 3.6's earlier Post-vente
     * allowance for slip reprinting). The deposit slip's "reversement net attendu" is computed
     * from what was deposited, not what was actually sold, so showing it again in Post-vente
     * risks contradicting the settlement report PDF (story 5.2), which already covers the
     * seller's post-vente paper trail with the real sold/unsold breakdown and actual net payout;
     * the labels follow the same rule now that {@code /volunteer/deposit} is no longer reachable
     * in Post-vente. Keeps the {@code deposit-reprint-not-allowed} error type (not
     * {@code item-modification-locked}) so the two reprint endpoints stay distinguishable.
     */
    public static void requireDepositPhaseForReprint(Edition edition) {
        if (edition.getPhase() != PhaseType.DEPOSIT) {
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
