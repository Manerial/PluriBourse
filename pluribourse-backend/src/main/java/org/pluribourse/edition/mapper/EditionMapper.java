package org.pluribourse.edition.mapper;

import org.mapstruct.*;
import org.pluribourse.edition.dto.*;
import org.pluribourse.edition.entity.*;
import org.pluribourse.user.enums.*;

import java.math.*;

@Mapper(componentModel = "spring", imports = java.time.LocalDate.class)
public interface EditionMapper {

    EditionDto toDto(Edition edition);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "archived", ignore = true)
    @Mapping(target = "phase", constant = "PREPARATION")
    @Mapping(target = "createdAt", expression = "java(LocalDate.now())")
    @Mapping(target = "commissionRate", source = "commissionRate")
    @Mapping(target = "documentLanguage", source = "documentLanguage")
    Edition toEntity(EditionDto dto, BigDecimal commissionRate, Language documentLanguage);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "phase", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "archived", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "commissionRate", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "documentLanguage", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEditionFromDto(EditionDto dto, @MappingTarget Edition edition);
}
