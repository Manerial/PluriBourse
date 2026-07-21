package org.pluribourse.domain.edition.mapper;

import org.mapstruct.*;
import org.pluribourse.domain.edition.dto.*;
import org.pluribourse.domain.edition.entity.*;

import java.util.*;

@Mapper(componentModel = "spring")
public interface EditionCategoryMapper {

    @Mapping(target = "tableNumbers", expression = "java(sortedTableNumbers(category))")
    EditionCategoryDto toDto(EditionCategory category);

    @Mapping(target = "id", ignore = true)
    EditionCategory toEntity(EditionCategoryDto dto, int displayOrder);

    default List<Integer> sortedTableNumbers(EditionCategory category) {
        List<Integer> sorted = new ArrayList<>(category.getTableNumbers());
        sorted.sort(Integer::compareTo);
        return sorted;
    }
}
