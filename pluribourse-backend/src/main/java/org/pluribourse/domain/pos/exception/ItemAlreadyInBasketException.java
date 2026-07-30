package org.pluribourse.domain.pos.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class ItemAlreadyInBasketException extends BusinessException {

    public ItemAlreadyInBasketException(Long itemId) {
        super(HttpStatus.CONFLICT, "item-already-in-basket", "Item already in basket: " + itemId);
    }
}
