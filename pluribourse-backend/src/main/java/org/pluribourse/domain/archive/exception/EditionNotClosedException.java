package org.pluribourse.domain.archive.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class EditionNotClosedException extends BusinessException {

    public EditionNotClosedException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "edition-not-closed",
                "Edition must be closed before it can be archived.");
    }
}
