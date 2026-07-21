package org.pluribourse.domain.seller.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class SellerEmailAlreadyExistsException extends BusinessException {

    public SellerEmailAlreadyExistsException() {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "seller-email-already-exists",
                "A seller with this email already exists for the active edition.");
    }
}
