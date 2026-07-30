package org.pluribourse.domain.item.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class ItemNotFoundException extends BusinessException {

    public ItemNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "item-not-found", "Item not found: " + id);
    }

    /**
     * Used by POS scan lookups (Story 4.1): covers both a malformed barcode and a well-formed
     * one with no matching item — a single "item not found" contract for the volunteer, not two.
     */
    public ItemNotFoundException(String barcode) {
        super(HttpStatus.NOT_FOUND, "item-not-found", "Item not found for barcode: " + barcode);
    }
}
