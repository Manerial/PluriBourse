package org.pluribourse.instanceconfig.mapper;

import org.mapstruct.*;
import org.pluribourse.instanceconfig.dto.*;
import org.pluribourse.instanceconfig.entity.*;

@Mapper(componentModel = "spring")
public interface GlobalInstanceConfigMapper {

    GlobalInstanceConfigDto toDto(GlobalInstanceConfig config);

    @Mapping(target = "id", ignore = true)
    void updateConfigFromDto(GlobalInstanceConfigDto dto, @MappingTarget GlobalInstanceConfig config);
}
