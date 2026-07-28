package org.pluribourse.domain.print.dto;

import java.time.LocalDate;

/**
 * {@code name} is {@code null} when PrinterBridge is unreachable at the time of listing, or no
 * longer reports this printerBridgeId — the frontend falls back to displaying the raw id in
 * either case.
 */
public record IgnoredPrinterDto(
        String printerBridgeId,
        String name,
        LocalDate ignoredAt
) {
}
