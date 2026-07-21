package org.pluribourse.domain.item.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class ItemModificationNotAllowedException extends BusinessException {

    public ItemModificationNotAllowedException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "item-modification-locked",
                "Items can only be created, modified or deleted while the edition is in the Deposit phase.");
    }
}
