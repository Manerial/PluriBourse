package org.pluribourse.domain.archive.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class NoItemsToArchiveException extends BusinessException {

    public NoItemsToArchiveException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "no-items-to-archive",
                "Edition has no item records to archive.");
    }
}
