package org.pluribourse.domain.payout.dto;

import org.pluribourse.domain.payout.entity.SettlementStatus;

/**
 * Server-side seller filter for bulk settlement report printing (story 5.6, FR-097) — mirrors
 * the frontend's {@code StatusFilter}. {@code SETTLED} matches both {@link SettlementStatus#SETTLED}
 * and {@link SettlementStatus#UNCLAIMED}, the same grouping already used by the "Soldés" filter
 * in {@code settlement-list.component.ts}.
 */
public enum SettlementFilter {
    ALL {
        @Override public boolean matches(SettlementStatus status) { return true; }
    },
    UNSETTLED {
        @Override public boolean matches(SettlementStatus status) { return status == SettlementStatus.UNSETTLED; }
    },
    SETTLED {
        @Override public boolean matches(SettlementStatus status) { return status != SettlementStatus.UNSETTLED; }
    };

    public abstract boolean matches(SettlementStatus status);
}
