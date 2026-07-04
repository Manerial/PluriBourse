package org.pluribourse.edition.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class CategoriesInUseException extends BusinessException {

    public CategoriesInUseException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "categories-in-use",
                "Categories cannot be modified while items are still registered for this edition.");
    }
}
