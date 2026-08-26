package org.pluribourse.domain.item.dto;

import jakarta.validation.*;
import jakarta.validation.constraints.*;

import java.math.*;
import java.util.*;

public record CreateLotDto(
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
        BigDecimal globalPrice,
        @NotNull
        @Valid
        @Size(min = 2, max = 50, message = "A lot must contain between 2 and 50 items")
        List<CreateLotItemDto> items
) {
}
