package org.pluribourse.domain.item.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class ItemNotFoundException extends BusinessException {

    public ItemNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "item-not-found", "Item not found: " + id);
    }
}
