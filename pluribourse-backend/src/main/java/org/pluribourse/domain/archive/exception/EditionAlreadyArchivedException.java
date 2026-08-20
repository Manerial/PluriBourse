package org.pluribourse.domain.archive.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class EditionAlreadyArchivedException extends BusinessException {

    public EditionAlreadyArchivedException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "edition-already-archived",
                "Edition has already been archived.");
    }
}
