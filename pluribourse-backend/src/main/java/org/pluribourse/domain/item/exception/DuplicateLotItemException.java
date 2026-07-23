package org.pluribourse.domain.item.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class DuplicateLotItemException extends BusinessException {

    public DuplicateLotItemException(Long id) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "duplicate-lot-item-id", "Item id " + id + " appears more than once in the submitted lot.");
    }
}
