package org.pluribourse.domain.print.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class PrinterQueueNotSuspendedException extends BusinessException {

    public PrinterQueueNotSuspendedException(Long printerId) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "printer-queue-not-suspended",
                "Printer queue is not suspended: " + printerId);
    }
}
