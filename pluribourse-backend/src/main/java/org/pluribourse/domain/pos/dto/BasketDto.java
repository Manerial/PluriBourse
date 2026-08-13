package org.pluribourse.domain.pos.dto;

import java.math.BigDecimal;
import java.util.List;

public record BasketDto(
        Long id,
        List<ScanResultDto> items,
        List<LotGroupDto> lotGroups,
        BigDecimal total
) {
}
