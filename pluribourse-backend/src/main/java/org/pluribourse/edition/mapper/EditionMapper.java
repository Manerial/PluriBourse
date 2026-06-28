package org.pluribourse.edition.mapper;

import org.mapstruct.*;
import org.pluribourse.edition.dto.EditionDto;
import org.pluribourse.edition.entity.Edition;

@Mapper(componentModel = "spring")
public interface EditionMapper {
    EditionDto toDto(Edition edition);
}
