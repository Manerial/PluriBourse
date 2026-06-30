package org.pluribourse.edition.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class CategoryMissingTableException extends BusinessException {

    public CategoryMissingTableException(String categoryName) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "category-missing-table",
                "Category '" + categoryName + "' must have at least one table assigned.");
    }
}
