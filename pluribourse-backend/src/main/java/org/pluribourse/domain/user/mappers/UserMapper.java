package org.pluribourse.domain.user.mappers;

import org.mapstruct.*;
import org.pluribourse.domain.user.dtos.CreateUserDto;
import org.pluribourse.domain.user.dtos.UserDto;
import org.pluribourse.domain.user.entities.User;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "role", expression = "java(user.getRole().name())")
    @Mapping(target = "enabled", expression = "java(user.getEnabled() == null || user.getEnabled())")
    UserDto toDto(User user);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "sellerProfileId", ignore = true)
    @Mapping(target = "preferredLanguage", constant = "EN")
    @Mapping(target = "languageInitialized", constant = "false")
    @Mapping(target = "forcePasswordChange", constant = "true")
    @Mapping(target = "enabled", constant = "true")
    User toEntity(CreateUserDto dto);
}
