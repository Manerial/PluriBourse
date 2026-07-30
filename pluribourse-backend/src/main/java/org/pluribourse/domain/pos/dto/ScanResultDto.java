package org.pluribourse.domain.pos.dto;

import java.math.BigDecimal;

public record ScanResultDto(
        Long itemId,
        String name,
        BigDecimal price,
        boolean incomplete,
        String comment
) {
}
