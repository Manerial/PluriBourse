package org.pluribourse.domain.edition.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class SourceEditionNotClosedException extends BusinessException {

    public SourceEditionNotClosedException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "source-edition-not-closed",
                "Can only copy categories from a CLOSED edition.");
    }
}
