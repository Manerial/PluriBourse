package org.pluribourse.user.controllers;

import jakarta.validation.*;
import lombok.*;
import org.pluribourse.user.dtos.*;
import org.pluribourse.user.entities.*;
import org.pluribourse.user.services.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.security.core.context.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('ADMIN', 'VOLUNTEER')")
    public ResponseEntity<UserSessionDto> me(Authentication authentication) {
        var userDetails = (PluriBourseUserDetails) authentication.getPrincipal();
        var dto = new UserSessionDto(
                userDetails.getUsername(),
                userDetails.getRole(),
                userDetails.isForcePasswordChange()
        );
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/change-password")
    @PreAuthorize("hasAnyRole('ADMIN', 'VOLUNTEER')")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordDto dto,
            Authentication authentication) {
        var userDetails = (PluriBourseUserDetails) authentication.getPrincipal();
        var newDetails = userService.changePassword(userDetails.getUserId(), dto.newPassword());

        // Refresh the SecurityContext so ForcePasswordChangeFilter sees the updated flag
        var newAuth = UsernamePasswordAuthenticationToken.authenticated(
                newDetails, null, newDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(newAuth);

        return ResponseEntity.ok().build();
    }
}
