package org.pluribourse.edition.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class EditionCannotBeDeletedException extends BusinessException {

    public EditionCannotBeDeletedException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "edition-cannot-be-deleted",
                "Editions that have progressed past Preparation phase cannot be deleted.");
    }
}
