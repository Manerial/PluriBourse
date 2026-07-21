package org.pluribourse.domain.user.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class UsernameTakenException extends BusinessException {

    public UsernameTakenException() {
        super(HttpStatus.CONFLICT, "username-already-taken", "Username already taken");
    }
}
