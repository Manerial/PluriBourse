package org.pluribourse.domain.archive.mapper;

import org.mapstruct.*;
import org.pluribourse.domain.archive.dto.*;
import org.pluribourse.domain.archive.entity.*;

import java.util.*;

@Mapper(componentModel = "spring")
public interface ArchivedItemMapper {

    ArchivedItemDto toDto(ArchivedItem item);

    List<ArchivedItemDto> toDtos(List<ArchivedItem> items);
}
