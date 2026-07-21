package org.pluribourse.domain.edition.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class NoCategoriesConfiguredException extends BusinessException {

    public NoCategoriesConfiguredException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "no-categories-configured",
                "At least one category must be configured before entering the Deposit phase.");
    }
}
