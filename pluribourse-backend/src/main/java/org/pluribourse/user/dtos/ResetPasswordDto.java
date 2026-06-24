package org.pluribourse.user.dtos;

import jakarta.validation.constraints.*;

public record ResetPasswordDto(
        @NotBlank
        @Size(min = 8, max = 128)
        @Pattern(regexp = ".*[A-Z].*", message = "must contain at least one uppercase letter")
        @Pattern(regexp = ".*[0-9].*", message = "must contain at least one digit")
        String newPassword
) {}
