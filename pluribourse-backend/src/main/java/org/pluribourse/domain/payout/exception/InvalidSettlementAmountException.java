package org.pluribourse.domain.payout.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a settlement's given amount is strictly greater than the computed net amount due
 * (FR-051) — a strictly lower amount is only a warning, not an error (AC 2).
 */
public class InvalidSettlementAmountException extends BusinessException {

    public InvalidSettlementAmountException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "invalid-settlement-amount", "Amount given exceeds the net amount due");
    }
}
