package org.pluribourse.domain.archive.dto;

/**
 * Internal filter carrier built by {@code ArchivedItemController} from individual
 * {@code @RequestParam}s — never bound directly from the request, same rationale as
 * {@code ItemCatalogFilterDto}.
 */
public record ArchivedItemFilterDto(
        String name,
        String categoryName,
        Boolean sold,
        int page,
        int size,
        String sort
) {
}
