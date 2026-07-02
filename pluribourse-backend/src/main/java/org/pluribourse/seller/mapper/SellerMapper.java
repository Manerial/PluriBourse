package org.pluribourse.seller.mapper;

import org.mapstruct.*;
import org.pluribourse.seller.dto.*;
import org.pluribourse.seller.entity.*;

import java.util.*;

@Mapper(componentModel = "spring")
public interface SellerMapper {
    List<SellerDto> toDtos(List<SellerProfile> sellers);

    SellerDto toDto(SellerProfile seller);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "edition", ignore = true)
    SellerProfile toEntity(SellerDto dto);
}
