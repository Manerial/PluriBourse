package org.pluribourse.user.dtos;

import jakarta.validation.constraints.*;
import org.pluribourse.user.enums.*;

public record CreateUserDto(
        @NotBlank
        @Size(max = 50)
        String firstName,
        @NotBlank
        @Size(max = 50)
        String lastName,
        @NotBlank
        @Size(max = 50)
        String username,
        @NotBlank
        @Size(min = 8, max = 128)
        @Pattern(regexp = ".*[A-Z].*", message = "must contain at least one uppercase letter")
        @Pattern(regexp = ".*[0-9].*", message = "must contain at least one digit")
        String password,
        @NotNull
        Role role
) {
}
