package org.pluribourse.domain.pos.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a CASH payment's {@code amountGiven} is strictly less than the basket total
 * (including negative amounts). The client already blocks this in the payment dialog, but the
 * server is the actual source of truth for a direct API call.
 */
public class InvalidAmountGivenException extends BusinessException {

    public InvalidAmountGivenException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "invalid-amount-given", "Amount given is less than the basket total");
    }
}
