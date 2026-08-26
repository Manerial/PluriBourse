package org.pluribourse.domain.item.dto;

import jakarta.validation.constraints.*;

public record CreateLotItemDto(
        @NotBlank
        @Size(max = 200)
        String name,
        boolean incomplete,
        @Size(max = 500)
        String comment
) {
}
