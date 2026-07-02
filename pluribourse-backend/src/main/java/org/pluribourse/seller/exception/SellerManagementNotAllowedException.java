package org.pluribourse.seller.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class SellerManagementNotAllowedException extends BusinessException {

    public SellerManagementNotAllowedException() {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "seller-management-locked",
                "Seller profiles can only be searched or created while the edition is in the Deposit phase.");
    }
}
