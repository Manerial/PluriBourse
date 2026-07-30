package org.pluribourse.domain.pos.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class EmptyBasketException extends BusinessException {

    public EmptyBasketException(Long basketId) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "basket-empty", "Basket is empty: " + basketId);
    }
}
