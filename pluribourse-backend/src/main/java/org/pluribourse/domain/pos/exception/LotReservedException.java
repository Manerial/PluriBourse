package org.pluribourse.domain.pos.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * FR-109 (SCP 2026-09-03) — a lot is checked out by exactly one basket at a time. Thrown when a POS
 * add-item (or, defensively, a validation) touches a lot already reserved for another basket. Same
 * shape as {@link LotAlreadySoldException}: {@code GlobalExceptionHandler.handleBusiness} maps it to
 * {@code 409} with {@code type = https://pluribourse/errors/lot-reserved}, no dedicated handler
 * needed. Carries only the {@code lotId} — never any seller data (CLAUDE.md: no personal data in logs).
 */
public class LotReservedException extends BusinessException {

    public LotReservedException(Long lotId) {
        super(HttpStatus.CONFLICT, "lot-reserved", "Lot reserved by another basket: " + lotId);
    }
}
