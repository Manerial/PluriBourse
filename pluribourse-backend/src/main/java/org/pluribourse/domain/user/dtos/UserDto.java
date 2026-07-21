package org.pluribourse.domain.user.dtos;

public record UserDto(
        Long id,
        String firstName,
        String lastName,
        String username,
        String role,
        boolean enabled
) {
}
