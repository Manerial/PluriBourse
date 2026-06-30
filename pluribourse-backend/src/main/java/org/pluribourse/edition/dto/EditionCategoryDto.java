package org.pluribourse.edition.dto;

import jakarta.validation.constraints.*;

import java.util.*;

public record EditionCategoryDto(
        Long id,
        @NotBlank
        @Size(max = 100)
        String name,
        @NotEmpty
        List<Integer> tableNumbers
) {
}
