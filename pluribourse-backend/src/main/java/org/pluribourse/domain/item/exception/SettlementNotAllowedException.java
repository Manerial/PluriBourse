package org.pluribourse.domain.item.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class SettlementNotAllowedException extends BusinessException {

    public SettlementNotAllowedException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "settlement-not-allowed",
                "Seller settlement is only available while the edition is in the Post-vente phase.");
    }
}
