package org.pluribourse.user.dtos;

import jakarta.validation.constraints.*;

public record ResetPasswordDto(
        @NotBlank @Size(min = 8, max = 128) String newPassword
) {}
