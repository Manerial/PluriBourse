package org.pluribourse.domain.edition.exception;

import org.pluribourse.shared.exception.*;
import org.springframework.http.*;

public class NoActiveEditionException extends BusinessException {

    public NoActiveEditionException() {
        super(HttpStatus.NOT_FOUND, "no-active-edition", "No edition is currently active.");
    }
}
