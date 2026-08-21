package org.pluribourse.domain.archive.dto;

public record ArchivedItemDto(
        Long id,
        String name,
        String categoryName,
        boolean sold
) {
}
