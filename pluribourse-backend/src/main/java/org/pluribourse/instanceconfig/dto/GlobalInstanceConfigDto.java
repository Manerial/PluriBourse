package org.pluribourse.instanceconfig.dto;

import jakarta.validation.constraints.*;
import org.pluribourse.user.enums.*;

import java.math.*;

public record GlobalInstanceConfigDto(
        @NotBlank @Size(max = 255) String associationName,
        @NotNull
        @DecimalMin("0.00") @DecimalMax("100.00")
        @Digits(integer = 3, fraction = 2)
        BigDecimal defaultCommissionRate,
        @NotNull Language defaultDocumentLanguage
) {
}
