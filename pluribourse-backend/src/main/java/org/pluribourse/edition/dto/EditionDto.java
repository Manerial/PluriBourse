package org.pluribourse.edition.dto;

import jakarta.validation.constraints.*;
import org.pluribourse.edition.entity.*;
import org.pluribourse.user.enums.*;

import java.math.*;
import java.time.*;

public record EditionDto(
        Long id,
        @NotBlank
        @Size(max = 255)
        String name,
        PhaseType phase,
        @DecimalMin("0.00")
        @DecimalMax("100.00")
        @Digits(integer = 3, fraction = 2)
        BigDecimal commissionRate,
        Language documentLanguage,
        LocalDate createdAt,
        Boolean archived,
        @NotNull
        LocalDate startDate,
        @NotNull
        LocalDate endDate
) {
    @AssertTrue(message = "startDate must not be after endDate")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !startDate.isAfter(endDate);
    }
}
