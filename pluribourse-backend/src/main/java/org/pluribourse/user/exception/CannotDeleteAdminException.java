package org.pluribourse.user.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class CannotDeleteAdminException extends BusinessException {

    public CannotDeleteAdminException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "cannot-delete-admin", "Admin account cannot be deleted");
    }
}
