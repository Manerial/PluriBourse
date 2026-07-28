package org.pluribourse.domain.print.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.pluribourse.domain.print.entity.PrinterType;

public record CreatePrinterDto(
        @NotBlank
        @Size(max = 100)
        String name,
        @NotNull
        PrinterType type,
        Integer widthMm,
        @NotBlank
        @Size(max = 32)
        String printerBridgeId
) {
}
