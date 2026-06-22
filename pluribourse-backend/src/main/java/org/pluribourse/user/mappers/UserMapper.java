package org.pluribourse.user.mappers;

import org.mapstruct.*;
import org.pluribourse.user.dtos.UserDto;
import org.pluribourse.user.entities.User;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "role", expression = "java(user.getRole().name())")
    @Mapping(target = "enabled", expression = "java(user.getEnabled() == null || user.getEnabled())")
    UserDto toDto(User user);
}
