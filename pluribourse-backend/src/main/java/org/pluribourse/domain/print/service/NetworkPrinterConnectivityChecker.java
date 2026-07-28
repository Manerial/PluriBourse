package org.pluribourse.domain.print.service;

import lombok.RequiredArgsConstructor;
import org.pluribourse.domain.print.entity.Printer;
import org.pluribourse.domain.print.entity.PrinterStatus;
import org.pluribourse.domain.print.entity.PrinterType;
import org.springframework.stereotype.Component;

/**
 * Delegates the connectivity check to PrinterBridge (story 3.11) instead of opening a TCP socket
 * directly — the backend runs in a container and can no longer reach network printers or
 * PrinterBridge's own host without going through it. {@link PrinterBridgeClient#checkStatus}
 * already distinguishes "PrinterBridge unreachable" ({@link
 * org.pluribourse.domain.print.exception.PrinterBridgeUnavailableException}, propagated as-is)
 * from "this printer specifically reported offline" (translated to {@link IllegalStateException}
 * here, same as before this story).
 */
@Component
@RequiredArgsConstructor
public class NetworkPrinterConnectivityChecker implements PrinterConnectivityChecker {

    private final PrinterBridgeClient printerBridgeClient;

    @Override
    public PrinterType getSupportedType() {
        return PrinterType.A4;
    }

    @Override
    public void checkAccessibility(Printer printer) {
        PrinterStatus status = printerBridgeClient.checkStatus(printer.getPrinterBridgeId()).status();
        // UNKNOWN is treated as accessible — same "fail open" philosophy as PrinterBridge's own
        // isLikelyRealDevice(): never hide a printer out of excess caution.
        if (status == PrinterStatus.OFFLINE) {
            throw new IllegalStateException(
                    "Printer " + printer.getPrinterBridgeId() + " reported offline by PrinterBridge");
        }
    }
}
