package org.pluribourse.domain.item.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class SalePhaseRequiredException extends BusinessException {

    public SalePhaseRequiredException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "sale-phase-required",
                "POS scanning is only available while the edition is in the Sale phase.");
    }
}
