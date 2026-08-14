package org.pluribourse.domain.payout.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class SellerAlreadySettledException extends BusinessException {

    public SellerAlreadySettledException(Long sellerId) {
        super(HttpStatus.CONFLICT, "seller-already-settled", "Seller already settled: " + sellerId);
    }
}
