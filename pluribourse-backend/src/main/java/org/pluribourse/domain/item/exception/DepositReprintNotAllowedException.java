package org.pluribourse.domain.item.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class DepositReprintNotAllowedException extends BusinessException {

    public DepositReprintNotAllowedException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "deposit-reprint-not-allowed",
                "The deposit slip can only be reprinted while the edition is in the Deposit or Post-sale phase.");
    }
}
