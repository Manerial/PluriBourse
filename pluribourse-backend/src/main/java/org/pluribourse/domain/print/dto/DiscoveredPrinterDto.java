package org.pluribourse.domain.print.dto;

import org.pluribourse.domain.print.entity.PrinterStatus;
import org.pluribourse.domain.print.entity.PrinterType;

public record DiscoveredPrinterDto(
        String printerBridgeId,
        String name,
        PrinterType type,
        PrinterStatus status
) {
}
