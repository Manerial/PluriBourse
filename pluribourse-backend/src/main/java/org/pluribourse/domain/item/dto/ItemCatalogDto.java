package org.pluribourse.domain.item.dto;

import java.math.*;

public record ItemCatalogDto(
        Long id,
        String barcode,
        String name,
        BigDecimal price,
        boolean incomplete,
        boolean sold,
        String categoryName,
        Integer tableNumber,
        String sellerFirstName,
        String sellerLastName,
        Long lotId,
        String lotName
) {
}
