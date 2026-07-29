package org.pluribourse.domain.item.dto;

/**
 * Internal filter carrier built by {@code ItemCatalogController} from individual
 * {@code @RequestParam}s — never bound directly from the request (unlike JPageFlow's
 * {@code FilterDto}), since several of these dimensions (barcode, table number) need
 * filtering semantics that {@code FilterDto}'s reflection-based mechanism cannot provide.
 */
public record ItemCatalogFilterDto(
        String name,
        String barcode,
        Long categoryId,
        Integer tableNumber,
        Boolean sold,
        Boolean incomplete,
        String sellerName,
        int page,
        int size,
        String sort
) {
}
