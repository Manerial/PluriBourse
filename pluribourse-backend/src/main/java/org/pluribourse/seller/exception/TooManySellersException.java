package org.pluribourse.seller.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class TooManySellersException extends BusinessException {

    public TooManySellersException(Long editionId) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "too-many-sellers",
                "Edition " + editionId + " already has the maximum number of sellers (FR-026 barcode format).");
    }
}
