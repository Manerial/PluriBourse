package org.pluribourse.domain.item.exception;

import org.pluribourse.shared.exception.*;
import org.springframework.http.*;

public class InvalidSortFieldException extends BusinessException {

    public InvalidSortFieldException(String field) {
        super(HttpStatus.BAD_REQUEST, "invalid-sort-field", "Cannot sort the catalog by field: " + field);
    }
}
