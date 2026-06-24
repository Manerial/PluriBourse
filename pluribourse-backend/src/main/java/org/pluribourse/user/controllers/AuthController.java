package org.pluribourse.user.controllers;

import jakarta.servlet.http.*;
import jakarta.validation.*;
import lombok.*;
import org.pluribourse.shared.security.*;
import org.pluribourse.user.dtos.*;
import org.pluribourse.user.entities.*;
import org.pluribourse.user.services.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final SecurityContextHelper securityContextHelper;

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('ADMIN', 'VOLUNTEER')")
    public ResponseEntity<UserSessionDto> me(Authentication authentication) {
        PluriBourseUserDetails userDetails = (PluriBourseUserDetails) authentication.getPrincipal();
        UserSessionDto dto = new UserSessionDto(
                userDetails.getUsername(),
                userDetails.getRole(),
                userDetails.isForcePasswordChange(),
                userDetails.getPreferredLanguage().name()
        );
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/change-password")
    @PreAuthorize("hasAnyRole('ADMIN', 'VOLUNTEER')")
    public ResponseEntity<UserSessionDto> changePassword(
            @Valid @RequestBody ChangePasswordDto dto,
            Authentication authentication,
            HttpServletRequest request) {
        PluriBourseUserDetails userDetails = (PluriBourseUserDetails) authentication.getPrincipal();
        PluriBourseUserDetails newDetails = userService.changePassword(userDetails.getUserId(), dto.newPassword());
        securityContextHelper.refreshSessionPrincipal(newDetails, request);
        UserSessionDto responseDto = new UserSessionDto(
                newDetails.getUsername(),
                newDetails.getRole(),
                newDetails.isForcePasswordChange(),
                newDetails.getPreferredLanguage().name());
        return ResponseEntity.ok(responseDto);
    }
}
