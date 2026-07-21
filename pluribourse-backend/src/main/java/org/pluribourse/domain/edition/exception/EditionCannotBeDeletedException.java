package org.pluribourse.domain.edition.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class EditionCannotBeDeletedException extends BusinessException {

    public EditionCannotBeDeletedException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "edition-cannot-be-deleted",
                "This edition cannot be deleted in its current state.");
    }
}
