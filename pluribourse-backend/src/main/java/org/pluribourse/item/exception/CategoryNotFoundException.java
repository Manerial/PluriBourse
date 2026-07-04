package org.pluribourse.item.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class CategoryNotFoundException extends BusinessException {

    public CategoryNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "category-not-found", "Category not found: " + id);
    }
}
