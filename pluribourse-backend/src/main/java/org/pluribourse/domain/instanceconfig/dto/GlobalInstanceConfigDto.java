package org.pluribourse.domain.instanceconfig.dto;

import jakarta.validation.constraints.*;
import org.pluribourse.domain.user.enums.*;

import java.math.*;

public record GlobalInstanceConfigDto(
        @NotBlank @Size(max = 255) String associationName,
        @NotNull
        @DecimalMin("0.00") @DecimalMax("100.00")
        @Digits(integer = 3, fraction = 2)
        BigDecimal defaultCommissionRate,
        @NotNull Language defaultDocumentLanguage,
        @NotBlank @Size(max = 10) @Pattern(regexp = "^[A-Za-z0-9 €$£¥]*$") String defaultCurrency
) {
}
