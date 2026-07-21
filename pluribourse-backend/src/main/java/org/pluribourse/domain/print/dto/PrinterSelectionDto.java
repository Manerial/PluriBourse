package org.pluribourse.domain.print.dto;

import jakarta.validation.constraints.Positive;

public record PrinterSelectionDto(
        @Positive Long thermalPrinterId,
        @Positive Long a4PrinterId
) {
}
