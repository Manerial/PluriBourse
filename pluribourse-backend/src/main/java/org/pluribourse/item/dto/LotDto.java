package org.pluribourse.item.dto;

import java.math.*;
import java.util.*;

public record LotDto(
        Long id,
        String name,
        BigDecimal globalPrice,
        List<ItemDto> items
) {
}
