package org.pluribourse.edition.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class CategoryNameRequiredException extends BusinessException {

    public CategoryNameRequiredException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "category-name-required",
                "Category name must not be blank.");
    }
}
