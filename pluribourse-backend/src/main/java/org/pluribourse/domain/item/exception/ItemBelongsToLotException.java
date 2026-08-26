package org.pluribourse.domain.item.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class ItemBelongsToLotException extends BusinessException {

    public ItemBelongsToLotException(Long id) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "item-belongs-to-lot",
                "Item " + id + " belongs to a lot and can only be modified through the lot — its category and table are shared with every other member.");
    }
}
