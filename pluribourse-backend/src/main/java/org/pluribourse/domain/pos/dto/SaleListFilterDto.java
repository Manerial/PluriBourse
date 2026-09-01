package org.pluribourse.domain.pos.dto;

import java.time.LocalDateTime;

/**
 * Internal filter carrier built by {@code PosSaleController} from individual {@code @RequestParam}s
 * — never bound directly from the request, same pattern as {@code ItemCatalogFilterDto}.
 * {@code dateFrom}/{@code dateTo}/{@code cashier} are nullable (a missing bound = no limit on that
 * side); {@code cashier} is a case-insensitive match on {@code Sale.user.username}.
 */
public record SaleListFilterDto(
        LocalDateTime dateFrom,
        LocalDateTime dateTo,
        String cashier,
        int page,
        int size,
        String sort
) {
}
