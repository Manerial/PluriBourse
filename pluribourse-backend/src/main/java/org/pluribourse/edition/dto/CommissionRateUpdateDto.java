package org.pluribourse.edition.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CommissionRateUpdateDto(
        @NotNull
        @DecimalMin("0.00") @DecimalMax("100.00")
        @Digits(integer = 3, fraction = 2)
        BigDecimal commissionRate
) {}
