package org.pluribourse.user.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class CannotEnableAdminException extends BusinessException {

    public CannotEnableAdminException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "cannot-enable-admin", "Admin account cannot be enabled");
    }
}
