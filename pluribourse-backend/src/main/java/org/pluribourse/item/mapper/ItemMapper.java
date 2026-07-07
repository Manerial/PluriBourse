package org.pluribourse.item.mapper;

import org.mapstruct.*;
import org.pluribourse.item.dto.*;
import org.pluribourse.item.entity.*;

import java.util.*;

@Mapper(componentModel = "spring")
public interface ItemMapper {

    @Mapping(target = "sellerProfileId", source = "sellerProfile.id")
    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "categoryName", source = "category.name")
    @Mapping(target = "lotId", source = "lot.id")
    @Mapping(target = "lotName", source = "lot.name")
    @Mapping(target = "lotPrice", source = "lot.globalPrice")
    ItemDto toDto(Item item);

    List<ItemDto> toDtos(List<Item> items);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "edition", ignore = true)
    @Mapping(target = "sellerProfile", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "lot", ignore = true)
    @Mapping(target = "tableNumber", ignore = true)
    @Mapping(target = "version", ignore = true)
    Item toEntity(CreateItemDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "edition", ignore = true)
    @Mapping(target = "sellerProfile", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "lot", ignore = true)
    @Mapping(target = "tableNumber", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "incomplete", ignore = true)
    @Mapping(target = "comment", ignore = true)
    void updateEntityFromDto(CreateItemDto dto, @MappingTarget Item item);
}
