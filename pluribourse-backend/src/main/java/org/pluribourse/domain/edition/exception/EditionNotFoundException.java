package org.pluribourse.domain.edition.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class EditionNotFoundException extends BusinessException {

    public EditionNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "edition-not-found", "Edition not found: " + id);
    }
}
