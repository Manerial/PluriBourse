package org.pluribourse.domain.edition.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class EditionCannotBeUpdatedException extends BusinessException {

    public EditionCannotBeUpdatedException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "edition-cannot-be-updated",
                "This edition can only be edited during the Preparation phase.");
    }
}
