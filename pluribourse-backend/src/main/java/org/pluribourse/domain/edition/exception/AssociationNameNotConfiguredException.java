package org.pluribourse.domain.edition.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class AssociationNameNotConfiguredException extends BusinessException {

    public AssociationNameNotConfiguredException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "association-name-not-configured",
                "The association name must be configured before creating an edition.");
    }
}
