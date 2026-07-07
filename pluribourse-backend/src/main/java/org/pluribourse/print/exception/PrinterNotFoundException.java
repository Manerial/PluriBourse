package org.pluribourse.print.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class PrinterNotFoundException extends BusinessException {

    public PrinterNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "printer-not-found", "Printer not found: " + id);
    }
}
