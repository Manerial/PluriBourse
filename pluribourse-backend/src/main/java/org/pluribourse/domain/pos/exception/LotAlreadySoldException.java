package org.pluribourse.domain.pos.exception;

import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * FR-109 (story 5.8) — a lot is sold at most once. Thrown when a POS scan or basket validation
 * touches a lot that already has at least one member sold, or when two terminals race to sell
 * different members of the same lot. Same shape as {@link org.pluribourse.domain.item.exception.ItemAlreadySoldException}:
 * {@code GlobalExceptionHandler.handleBusiness} maps it to {@code 409} with
 * {@code type = https://pluribourse/errors/lot-already-sold}, no dedicated handler needed. Carries
 * only the {@code lotId} — never any seller data (CLAUDE.md: no personal data in logs).
 */
public class LotAlreadySoldException extends BusinessException {

    public LotAlreadySoldException(Long lotId) {
        super(HttpStatus.CONFLICT, "lot-already-sold", "Lot already sold: " + lotId);
    }
}
