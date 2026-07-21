package org.pluribourse.domain.print.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class InvalidPrinterSelectionException extends BusinessException {

    public InvalidPrinterSelectionException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "invalid-printer-selection", message);
    }
}
