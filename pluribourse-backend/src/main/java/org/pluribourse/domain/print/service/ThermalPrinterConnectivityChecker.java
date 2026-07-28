package org.pluribourse.domain.print.service;

import lombok.RequiredArgsConstructor;
import org.pluribourse.domain.print.entity.Printer;
import org.pluribourse.domain.print.entity.PrinterStatus;
import org.pluribourse.domain.print.entity.PrinterType;
import org.springframework.stereotype.Component;

/**
 * Renamed from {@code SerialPrinterConnectivityChecker} (story 3.11) — it no longer opens a
 * serial port itself, PrinterBridge does. Delegates to {@link PrinterBridgeClient#checkStatus},
 * same distinction between "PrinterBridge unreachable" (propagated) and "this printer reported
 * offline" (translated to {@link IllegalStateException}) as {@link
 * NetworkPrinterConnectivityChecker}.
 */
@Component
@RequiredArgsConstructor
public class ThermalPrinterConnectivityChecker implements PrinterConnectivityChecker {

    private final PrinterBridgeClient printerBridgeClient;

    @Override
    public PrinterType getSupportedType() {
        return PrinterType.THERMAL;
    }

    @Override
    public void checkAccessibility(Printer printer) {
        PrinterStatus status = printerBridgeClient.checkStatus(printer.getPrinterBridgeId()).status();
        if (status == PrinterStatus.OFFLINE) {
            throw new IllegalStateException(
                    "Printer " + printer.getPrinterBridgeId() + " reported offline by PrinterBridge");
        }
    }
}
