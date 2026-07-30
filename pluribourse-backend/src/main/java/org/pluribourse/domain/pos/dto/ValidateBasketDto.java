package org.pluribourse.domain.pos.dto;

import jakarta.validation.constraints.NotNull;
import org.pluribourse.domain.pos.entity.PaymentMethod;

import java.math.BigDecimal;

public record ValidateBasketDto(
        @NotNull PaymentMethod paymentMethod,
        BigDecimal amountGiven
) {
}
