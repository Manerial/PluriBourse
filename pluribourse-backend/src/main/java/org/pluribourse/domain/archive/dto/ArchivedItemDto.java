package org.pluribourse.domain.archive.dto;

import java.math.BigDecimal;

public record ArchivedItemDto(
        Long id,
        String name,
        String categoryName,
        boolean sold,
        BigDecimal price,
        Long lotRef,
        String lotName
) {
}
