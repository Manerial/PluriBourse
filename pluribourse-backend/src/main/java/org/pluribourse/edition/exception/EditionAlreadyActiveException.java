package org.pluribourse.edition.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class EditionAlreadyActiveException extends BusinessException {

    public EditionAlreadyActiveException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "edition-already-active",
                "An edition is already active. Close the current edition before creating a new one.");
    }
}
