package org.pluribourse.domain.pos.exception;

import lombok.Getter;
import org.pluribourse.domain.pos.dto.ConflictingItemDto;
import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Thrown at basket validation (AC 8) when one or more items were sold on another checkout since
 * they were scanned into this basket — detected either up-front (already {@code sold}) or via the
 * optimistic lock ({@code Item.@Version}) failing at flush. Carries the conflicting items so the
 * frontend can list them by name; {@link org.pluribourse.shared.exception.GlobalExceptionHandler}
 * surfaces this list as an extra {@code conflictingItems} property on the RFC 7807 body.
 */
@Getter
public class BasketValidationConflictException extends BusinessException {

    private final List<ConflictingItemDto> conflictingItems;

    public BasketValidationConflictException(List<ConflictingItemDto> conflictingItems) {
        super(HttpStatus.CONFLICT, "basket-validation-conflict",
                "One or more items in the basket were sold on another checkout.");
        this.conflictingItems = conflictingItems;
    }
}
