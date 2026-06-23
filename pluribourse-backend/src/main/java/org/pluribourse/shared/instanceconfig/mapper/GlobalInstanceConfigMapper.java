package org.pluribourse.shared.instanceconfig.mapper;

import org.mapstruct.*;
import org.pluribourse.shared.instanceconfig.dto.GlobalInstanceConfigDto;
import org.pluribourse.shared.instanceconfig.entity.GlobalInstanceConfig;

@Mapper(componentModel = "spring")
public interface GlobalInstanceConfigMapper {

    GlobalInstanceConfigDto toDto(GlobalInstanceConfig config);
}
