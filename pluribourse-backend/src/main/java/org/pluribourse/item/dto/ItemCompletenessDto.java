package org.pluribourse.item.dto;

import jakarta.validation.constraints.*;

public record ItemCompletenessDto(
        boolean incomplete,
        @Size(max = 500)
        String comment
) {
}
