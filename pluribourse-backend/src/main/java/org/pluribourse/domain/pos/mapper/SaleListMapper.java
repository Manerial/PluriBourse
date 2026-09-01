package org.pluribourse.domain.pos.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.pluribourse.domain.pos.dto.SaleListItemDto;
import org.pluribourse.domain.pos.entity.Sale;

@Mapper(componentModel = "spring")
public interface SaleListMapper {

    @Mapping(target = "cashier", source = "sale.user.username")
    @Mapping(target = "currency", source = "currency")
    SaleListItemDto toDto(Sale sale, String currency);
}
