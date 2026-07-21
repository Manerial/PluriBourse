package org.pluribourse.domain.item.dto;

import java.math.*;

public record ItemDto(
        Long id,
        Long sellerProfileId,
        Long categoryId,
        String categoryName,
        String name,
        BigDecimal price,
        boolean incomplete,
        String comment,
        Integer tableNumber,
        Long lotId,
        String lotName,
        BigDecimal lotPrice
) {
}
