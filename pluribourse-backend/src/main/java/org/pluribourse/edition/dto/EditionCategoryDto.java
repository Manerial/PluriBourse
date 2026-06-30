package org.pluribourse.edition.dto;

import java.util.List;

public record EditionCategoryDto(
        Long id,
        String name,
        List<Integer> tableNumbers
) {}
