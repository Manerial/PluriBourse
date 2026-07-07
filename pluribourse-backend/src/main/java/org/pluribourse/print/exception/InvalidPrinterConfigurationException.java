package org.pluribourse.print.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class InvalidPrinterConfigurationException extends BusinessException {

    public InvalidPrinterConfigurationException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "invalid-printer-configuration", message);
    }
}
