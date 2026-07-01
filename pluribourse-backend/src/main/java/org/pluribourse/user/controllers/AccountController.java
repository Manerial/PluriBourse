package org.pluribourse.user.controllers;

import jakarta.servlet.http.*;
import lombok.*;
import org.pluribourse.shared.security.*;
import org.pluribourse.user.entities.*;
import org.pluribourse.user.enums.*;
import org.pluribourse.user.services.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.*;
import org.springframework.security.core.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/account")
@RequiredArgsConstructor
public class AccountController {

    private final UserService userService;
    private final SecurityContextHelper securityContextHelper;

    @PutMapping("/language-preference")
    @PreAuthorize("hasAnyRole('ADMIN', 'VOLUNTEER')")
    public ResponseEntity<Void> updateLanguagePreference(
            @RequestParam Language language,
            Authentication authentication,
            HttpServletRequest request) {
        PluriBourseUserDetails userDetails = (PluriBourseUserDetails) authentication.getPrincipal();
        PluriBourseUserDetails newDetails = userService.updateLanguagePreference(userDetails.getUserId(), language);
        securityContextHelper.refreshSessionPrincipal(newDetails, request);
        return ResponseEntity.ok().build();
    }
}
