package org.pluribourse.domain.pos.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class BasketLotNotFoundException extends BusinessException {

    public BasketLotNotFoundException(Long basketId, Long lotId) {
        super(HttpStatus.NOT_FOUND, "basket-lot-not-found",
                "Lot " + lotId + " not found in basket " + basketId);
    }
}
