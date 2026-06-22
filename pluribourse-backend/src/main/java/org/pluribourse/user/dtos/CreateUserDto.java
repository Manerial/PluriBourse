package org.pluribourse.user.dtos;

import jakarta.validation.constraints.*;

public record CreateUserDto(
        @NotBlank @Size(max = 50) String firstName,
        @NotBlank @Size(max = 50) String lastName,
        @NotBlank @Size(max = 50) String username,
        @NotBlank @Size(min = 8, max = 128) String password
) {}
