package org.pluribourse.user.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class CannotModifyAdminException extends BusinessException {

    public CannotModifyAdminException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "cannot-modify-admin", "Admin account cannot be modified");
    }
}
