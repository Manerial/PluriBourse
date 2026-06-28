package org.pluribourse.edition.dto;

import jakarta.validation.constraints.*;
import org.pluribourse.edition.entity.PhaseType;
import org.pluribourse.user.enums.Language;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EditionDto(
        Long id,
        @NotBlank @Size(max = 255) String name,
        PhaseType phase,
        @DecimalMin("0.00") @DecimalMax("100.00")
        @Digits(integer = 3, fraction = 2)
        BigDecimal commissionRate,
        Language documentLanguage,
        LocalDate createdAt
) {}
