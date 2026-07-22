package org.pluribourse.domain.print.dto;

import org.pluribourse.domain.print.entity.PrinterType;

public record PrinterSummaryDto(
        Long id,
        String name,
        PrinterType type,
        boolean connected
) {
}
