package org.pluribourse.user.dtos;

public record UserSessionDto(
        String username,
        String role,
        boolean forcePasswordChange,
        String preferredLanguage) {
}
