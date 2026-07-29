package org.pluribourse.domain.item.dto;

import org.springframework.data.domain.*;

public record ItemCatalogPageDto(
        Page<ItemCatalogDto> page
) {
}
