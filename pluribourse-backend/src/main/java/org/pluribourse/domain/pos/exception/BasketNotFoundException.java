package org.pluribourse.domain.pos.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Also thrown when the basket exists but belongs to another user — 404, never 403, so a
 * client-supplied {@code basketId} cannot be used to probe another volunteer's basket (IDOR).
 */
public class BasketNotFoundException extends BusinessException {

    public BasketNotFoundException(Long basketId) {
        super(HttpStatus.NOT_FOUND, "basket-not-found", "Basket not found: " + basketId);
    }
}
