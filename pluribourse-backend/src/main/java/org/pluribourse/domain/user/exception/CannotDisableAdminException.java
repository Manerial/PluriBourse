package org.pluribourse.domain.user.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class CannotDisableAdminException extends BusinessException {

    public CannotDisableAdminException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "cannot-disable-admin", "Admin account cannot be disabled");
    }
}
