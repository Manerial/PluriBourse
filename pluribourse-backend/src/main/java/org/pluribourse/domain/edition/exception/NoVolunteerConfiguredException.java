package org.pluribourse.domain.edition.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class NoVolunteerConfiguredException extends BusinessException {

    public NoVolunteerConfiguredException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "no-volunteer-configured",
                "At least one volunteer account must exist before entering the Deposit phase.");
    }
}
