package org.pluribourse.domain.pos.dto;

import org.pluribourse.domain.pos.entity.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One row of the sales list screen (story 4.7, FR-108). {@code currency} carries the active
 * edition's currency so the frontend can format the amount without a second call.
 */
public record SaleListItemDto(
        Long id,
        LocalDateTime soldAt,
        String cashier,
        PaymentMethod paymentMethod,
        BigDecimal total,
        String currency
) {
}
