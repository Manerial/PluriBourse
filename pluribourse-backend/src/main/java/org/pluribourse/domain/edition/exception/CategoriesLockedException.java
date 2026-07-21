package org.pluribourse.domain.edition.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class CategoriesLockedException extends BusinessException {

    public CategoriesLockedException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "categories-locked",
                "Categories and table assignments are locked once the Deposit phase has started.");
    }
}
