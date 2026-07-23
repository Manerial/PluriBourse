package org.pluribourse.domain.item.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class LotNotFoundException extends BusinessException {

    public LotNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "lot-not-found", "Lot not found: " + id);
    }
}
