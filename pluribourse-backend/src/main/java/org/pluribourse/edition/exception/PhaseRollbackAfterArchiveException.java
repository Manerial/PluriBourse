package org.pluribourse.edition.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class PhaseRollbackAfterArchiveException extends BusinessException {

    public PhaseRollbackAfterArchiveException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "phase-rollback-disabled-after-archive",
                "Cannot roll back from Closed phase after the edition has been archived.");
    }
}
