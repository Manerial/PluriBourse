package org.pluribourse.domain.pos.dto;

import java.math.BigDecimal;

public record LotGroupDto(
        Long lotId,
        String lotName,
        BigDecimal globalPrice,
        int scannedCount,
        int totalCount
) {
}
