package org.pluribourse.print.dto;

import org.pluribourse.print.entity.PrinterType;

public record AvailablePrinterDto(
        Long id,
        String name,
        PrinterType type
) {
}
