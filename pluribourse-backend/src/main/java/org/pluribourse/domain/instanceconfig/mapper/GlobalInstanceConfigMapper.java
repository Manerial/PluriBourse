package org.pluribourse.domain.instanceconfig.mapper;

import org.mapstruct.*;
import org.pluribourse.domain.instanceconfig.dto.*;
import org.pluribourse.domain.instanceconfig.entity.*;

@Mapper(componentModel = "spring")
public interface GlobalInstanceConfigMapper {

    GlobalInstanceConfigDto toDto(GlobalInstanceConfig config);

    @Mapping(target = "id", ignore = true)
    void updateConfigFromDto(GlobalInstanceConfigDto dto, @MappingTarget GlobalInstanceConfig config);
}
