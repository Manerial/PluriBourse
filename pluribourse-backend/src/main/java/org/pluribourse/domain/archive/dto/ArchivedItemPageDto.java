package org.pluribourse.domain.archive.dto;

import org.springframework.data.domain.*;

public record ArchivedItemPageDto(
        Page<ArchivedItemDto> page
) {
}
