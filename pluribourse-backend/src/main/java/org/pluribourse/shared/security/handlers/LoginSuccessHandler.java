package org.pluribourse.shared.security.handlers;

import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.*;
import org.jspecify.annotations.*;
import org.pluribourse.shared.security.*;
import org.pluribourse.user.dtos.*;
import org.pluribourse.user.entities.*;
import org.pluribourse.user.enums.*;
import org.pluribourse.user.services.*;
import org.springframework.security.core.*;
import org.springframework.security.web.authentication.*;
import org.springframework.stereotype.*;

import java.io.*;

@NullMarked
@Component
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final ObjectMapper objectMapper;
    private final UserService userService;
    private final SecurityContextHelper securityContextHelper;

    public LoginSuccessHandler(ObjectMapper objectMapper, UserService userService, SecurityContextHelper securityContextHelper) {
        this.objectMapper = objectMapper;
        this.userService = userService;
        this.securityContextHelper = securityContextHelper;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException {
        if (!(authentication.getPrincipal() instanceof PluriBourseUserDetails userDetails)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        Language effectiveLanguage = userDetails.getPreferredLanguage();
        if (!userDetails.isLanguageInitialized()) {
            effectiveLanguage = detectLanguage(request);
            PluriBourseUserDetails newDetails = userService.initializeLanguage(userDetails.getUserId(), effectiveLanguage);
            securityContextHelper.refreshSessionPrincipal(newDetails, request);
        }
        UserSessionDto dto = new UserSessionDto(
                userDetails.getUsername(),
                userDetails.getRole(),
                userDetails.isForcePasswordChange(),
                effectiveLanguage.name()
        );
        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_OK);
        objectMapper.writeValue(response.getWriter(), dto);
    }

    private Language detectLanguage(HttpServletRequest request) {
        String acceptLanguage = request.getHeader("Accept-Language");
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return Language.EN;
        }
        String lang = request.getLocale().getLanguage();
        return "fr".equals(lang) ? Language.FR : Language.EN;
    }
}
