package org.pluribourse.domain.pos.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Also thrown when the sale exists but belongs to another volunteer — 404, never 403, so a
 * client-supplied {@code saleId} cannot be used to probe another volunteer's sale (IDOR).
 */
public class SaleNotFoundException extends BusinessException {

    public SaleNotFoundException(Long saleId) {
        super(HttpStatus.NOT_FOUND, "sale-not-found", "Sale not found: " + saleId);
    }
}
