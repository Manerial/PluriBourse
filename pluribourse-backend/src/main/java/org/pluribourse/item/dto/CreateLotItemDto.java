package org.pluribourse.item.dto;

import jakarta.validation.constraints.*;

public record CreateLotItemDto(
        @NotNull
        Long categoryId,
        @NotBlank
        @Size(max = 200)
        String name,
        boolean incomplete,
        @Size(max = 500)
        String comment
) {
}
