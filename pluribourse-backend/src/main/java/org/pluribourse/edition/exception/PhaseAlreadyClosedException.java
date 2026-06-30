package org.pluribourse.edition.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class PhaseAlreadyClosedException extends BusinessException {

    public PhaseAlreadyClosedException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "phase-already-closed",
                "Edition is already closed. Cannot advance further.");
    }
}
