package org.pluribourse.domain.item.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class TooManyItemsException extends BusinessException {

    public TooManyItemsException(Long sellerProfileId) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "too-many-items",
                "Seller " + sellerProfileId + " already has the maximum number of items (FR-026 barcode format).");
    }
}
