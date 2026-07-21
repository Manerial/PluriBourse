package org.pluribourse.domain.user.dtos;

import org.pluribourse.domain.user.entities.PluriBourseUserDetails;

public record UserSessionDto(
        String username,
        String role,
        boolean forcePasswordChange,
        String preferredLanguage) {

    public static UserSessionDto from(PluriBourseUserDetails details) {
        return new UserSessionDto(
                details.getUsername(),
                details.getRole(),
                details.isForcePasswordChange(),
                details.getPreferredLanguage().name());
    }
}
