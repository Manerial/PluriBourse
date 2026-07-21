package org.pluribourse.domain.user.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class UserNotFoundException extends BusinessException {

    public UserNotFoundException() {
        super(HttpStatus.NOT_FOUND, "user-not-found", "User not found");
    }
}
