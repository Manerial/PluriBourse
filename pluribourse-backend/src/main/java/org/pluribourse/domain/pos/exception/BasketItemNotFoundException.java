package org.pluribourse.domain.pos.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class BasketItemNotFoundException extends BusinessException {

    public BasketItemNotFoundException(Long basketId, Long itemId) {
        super(HttpStatus.NOT_FOUND, "basket-item-not-found",
                "Item " + itemId + " not found in basket " + basketId);
    }
}
