package org.pluribourse.item.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class EmptyDepositException extends BusinessException {

    public EmptyDepositException(Long sellerProfileId) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "empty-deposit",
                "Seller " + sellerProfileId + " has no registered items to validate.");
    }
}
