package org.pluribourse.domain.print.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when PrinterBridge itself cannot be reached (timeout, connection refused) — distinct
 * from a printer-specific failure reported by PrinterBridge, which PrinterBridge itself answers
 * for. Extends {@link BusinessException} so it is handled by the existing generic RFC 7807
 * handler without a dedicated {@code @ExceptionHandler}.
 */
public class PrinterBridgeUnavailableException extends BusinessException {

    public PrinterBridgeUnavailableException(String message, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "printerbridge-unavailable", message);
        initCause(cause);
    }
}
