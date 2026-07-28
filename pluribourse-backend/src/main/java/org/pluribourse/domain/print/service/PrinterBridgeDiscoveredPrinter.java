package org.pluribourse.domain.print.service;

import org.pluribourse.domain.print.entity.PrinterStatus;

/**
 * Deserialized form of PrinterBridge's {@code Printer} JSON — returned identically by both
 * {@code GET /printers} and {@code GET /printers/{id}/status} (verified against PrinterBridge's
 * own {@code ApiServer}), so both {@link PrinterBridgeClient#discover()} and
 * {@link PrinterBridgeClient#checkStatus(String)} share this one type.
 */
public record PrinterBridgeDiscoveredPrinter(
        String id,
        String name,
        PrinterBridgePrinterType type,
        PrinterStatus status) {
}
