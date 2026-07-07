package org.pluribourse.print.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.pluribourse.print.entity.PrinterType;

public record CreatePrinterDto(
        @NotBlank
        @Size(max = 100)
        String name,
        @NotNull
        PrinterType type,
        @Size(max = 100)
        String serialPort,
        Integer widthMm,
        @Size(max = 255)
        String host,
        @Min(1)
        @Max(65535)
        Integer port
) {
}
