package org.pluribourse.domain.print.dto;

import org.pluribourse.domain.print.entity.PrinterType;

public record PrinterDto(
        Long id,
        String name,
        PrinterType type,
        String serialPort,
        Integer widthMm,
        String host,
        Integer port
) {
}
