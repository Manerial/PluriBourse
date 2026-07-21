package org.pluribourse.domain.print.dto;

public record PrinterSelectionStatusDto(
        boolean done,
        Long thermalPrinterId,
        Long a4PrinterId
) {
}
