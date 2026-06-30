package org.pluribourse.edition.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class CommissionRateFrozenException extends BusinessException {

    public CommissionRateFrozenException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "commission-rate-frozen",
                "Commission rate is locked once the Deposit phase has started.");
    }
}
