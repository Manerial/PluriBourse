package org.pluribourse.domain.item.dto;

import jakarta.validation.constraints.*;

import java.math.*;

public record CreateItemDto(
        @NotNull
        Long sellerProfileId,
        @NotNull
        Long categoryId,
        @NotBlank
        @Size(max = 200)
        String name,
        @NotNull
        @DecimalMin("0.01")
        @Digits(integer = 8, fraction = 2)
        BigDecimal price,
        boolean incomplete,
        @Size(max = 500)
        String comment
) {
}
