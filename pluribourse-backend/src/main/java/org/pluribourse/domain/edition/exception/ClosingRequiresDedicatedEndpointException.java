package org.pluribourse.domain.edition.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class ClosingRequiresDedicatedEndpointException extends BusinessException {

    public ClosingRequiresDedicatedEndpointException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "closing-requires-dedicated-endpoint",
                "Post-vente cannot advance to Clôturée through the generic phase-advance endpoint. Use the dedicated close endpoint instead.");
    }
}
