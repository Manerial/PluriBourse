package org.pluribourse.domain.item.dto;

import java.math.*;
import java.util.*;

public record LotDto(
        Long id,
        String name,
        BigDecimal globalPrice,
        Long categoryId,
        String categoryName,
        List<ItemDto> items
) {
}
