package org.pluribourse.domain.print.dto;

import org.pluribourse.domain.print.entity.PrinterType;

public record AvailablePrinterDto(
        Long id,
        String name,
        PrinterType type
) {
}
