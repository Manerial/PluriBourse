package org.pluribourse.domain.pos.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.pluribourse.domain.item.entity.Item;
import org.pluribourse.domain.pos.dto.ScanResultDto;

@Mapper(componentModel = "spring")
public interface ScanResultMapper {

    @Mapping(target = "itemId", source = "id")
    @Mapping(target = "lotId", source = "lot.id")
    ScanResultDto toDto(Item item);
}
