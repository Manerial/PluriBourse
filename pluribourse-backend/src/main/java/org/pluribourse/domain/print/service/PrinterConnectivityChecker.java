package org.pluribourse.domain.print.service;

import org.pluribourse.domain.print.entity.Printer;
import org.pluribourse.domain.print.entity.PrinterType;

public interface PrinterConnectivityChecker {

    PrinterType getSupportedType();

    /**
     * Throws an unchecked exception (message kept as the runtime {@code lastError}) when the
     * printer cannot be reached. Never blocks queue/thread creation — callers only record the
     * failure.
     */
    void checkAccessibility(Printer printer);
}
