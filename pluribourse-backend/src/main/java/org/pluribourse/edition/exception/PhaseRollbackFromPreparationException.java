package org.pluribourse.edition.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class PhaseRollbackFromPreparationException extends BusinessException {

    public PhaseRollbackFromPreparationException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "phase-cannot-rollback-from-preparation",
                "Cannot roll back from Preparation phase.");
    }
}
