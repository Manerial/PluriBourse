package org.pluribourse.domain.payout.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record SettleDto(
        @NotNull @DecimalMin(value = "0.00") @Digits(integer = 8, fraction = 2) BigDecimal amount) {
}
