package org.pluribourse.domain.pos.dto;

import org.springframework.data.domain.Page;

/**
 * Same envelope as {@code ItemCatalogPageDto} — a Spring {@code Page<T>} serialized directly
 * (story 6.1 pattern).
 */
public record SaleListPageDto(
        Page<SaleListItemDto> page
) {
}
