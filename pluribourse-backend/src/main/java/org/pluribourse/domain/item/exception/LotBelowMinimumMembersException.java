package org.pluribourse.domain.item.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class LotBelowMinimumMembersException extends BusinessException {

    public LotBelowMinimumMembersException(Long lotId) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "lot-below-minimum-members",
                "Lot " + lotId + " has only 2 members left — deleting one would break the minimum of 2 (FR-043). Delete the whole lot instead.");
    }
}
