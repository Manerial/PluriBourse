package org.pluribourse.domain.pos.dto;

import org.pluribourse.domain.pos.entity.PaymentMethod;

import java.math.BigDecimal;

public record SaleDto(
        Long id,
        BigDecimal total,
        PaymentMethod paymentMethod,
        BigDecimal amountGiven,
        BigDecimal changeDue
) {
}
