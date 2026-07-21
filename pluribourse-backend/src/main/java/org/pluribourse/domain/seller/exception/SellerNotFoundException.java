package org.pluribourse.domain.seller.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class SellerNotFoundException extends BusinessException {

    public SellerNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "seller-not-found", "Seller not found: " + id);
    }
}
