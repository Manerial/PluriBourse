package org.pluribourse.domain.edition.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class EditionAlreadyActiveException extends BusinessException {

    public EditionAlreadyActiveException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "edition-already-active",
                "Another edition is already in Deposit, Sale or Post-sale phase. It must reach Closed before this one can start Deposit.");
    }
}
