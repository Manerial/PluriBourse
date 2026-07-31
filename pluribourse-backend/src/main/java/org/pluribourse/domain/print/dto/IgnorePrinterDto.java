package org.pluribourse.domain.print.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IgnorePrinterDto(
        @NotBlank
        @Size(max = 100)
        String name
) {
}
