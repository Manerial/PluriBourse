package org.pluribourse.domain.item.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class ItemAlreadySoldException extends BusinessException {

    public ItemAlreadySoldException(Long itemId) {
        super(HttpStatus.CONFLICT, "item-already-sold", "Item already sold: " + itemId);
    }
}
