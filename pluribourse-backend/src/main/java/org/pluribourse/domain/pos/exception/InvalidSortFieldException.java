package org.pluribourse.domain.pos.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Twin of {@code org.pluribourse.domain.item.exception.InvalidSortFieldException} — same 400 /
 * {@code invalid-sort-field} contract, but with a message worded for the sales list (the item one
 * hardcodes "Cannot sort the catalog by field", inexact here). Not factored into a shared class:
 * a {@code pos} service depending on an {@code item} helper would be a poor architecture signal,
 * and CLAUDE.md discourages premature abstraction.
 */
public class InvalidSortFieldException extends BusinessException {

    public InvalidSortFieldException(String field) {
        super(HttpStatus.BAD_REQUEST, "invalid-sort-field", "Cannot sort the sales list by field: " + field);
    }
}
