package org.pluribourse.domain.print.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class IgnoredPrinterNotFoundException extends BusinessException {

    public IgnoredPrinterNotFoundException(String printerBridgeId) {
        super(HttpStatus.NOT_FOUND, "ignored-printer-not-found", "Ignored printer not found: " + printerBridgeId);
    }
}
