package org.pluribourse.domain.seller.exception;

import org.pluribourse.shared.exception.*;
import org.springframework.http.*;

public class SellerDeletionNotAllowedException extends BusinessException {

    public SellerDeletionNotAllowedException() {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "seller-deletion-locked",
                "A seller can only be deleted while the edition is in the Deposit phase.");
    }
}
