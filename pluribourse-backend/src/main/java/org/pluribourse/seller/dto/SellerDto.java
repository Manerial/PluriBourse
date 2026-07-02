package org.pluribourse.seller.dto;

import jakarta.validation.constraints.*;

public record SellerDto(
        Long id,
        @NotBlank
        @Size(max = 100)
        String firstName,
        @NotBlank
        @Size(max = 100)
        String lastName,
        @NotBlank
        @Email
        @Size(max = 255)
        String email,
        @NotBlank
        @Size(max = 30)
        String phone
) {
}
