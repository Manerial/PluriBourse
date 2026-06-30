package org.pluribourse.edition.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.pluribourse.edition.dto.EditionCategoryDto;
import org.pluribourse.edition.entity.EditionCategory;

import java.util.ArrayList;
import java.util.List;

@Mapper(componentModel = "spring")
public interface EditionCategoryMapper {

    @Mapping(target = "tableNumbers", expression = "java(sortedTableNumbers(category))")
    EditionCategoryDto toDto(EditionCategory category);

    default List<Integer> sortedTableNumbers(EditionCategory category) {
        List<Integer> sorted = new ArrayList<>(category.getTableNumbers());
        sorted.sort(Integer::compareTo);
        return sorted;
    }
}
