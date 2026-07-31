package org.pluribourse.domain.print.dto;

import java.time.LocalDate;

/**
 * {@code name} is captured once, when the printer is ignored (see {@code PrinterService#ignore}).
 * It is {@code null} only for printers ignored before that column existed — the frontend falls
 * back to displaying the raw id in that case.
 */
public record IgnoredPrinterDto(
        String printerBridgeId,
        String name,
        LocalDate ignoredAt
) {
}
