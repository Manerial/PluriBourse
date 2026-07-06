package org.pluribourse.shared.security.handlers;

import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.*;
import lombok.*;
import org.jspecify.annotations.*;
import org.pluribourse.shared.security.*;
import org.pluribourse.user.dtos.*;
import org.pluribourse.user.entities.*;
import org.pluribourse.user.enums.*;
import org.pluribourse.user.services.*;
import org.springframework.security.core.*;
import org.springframework.security.web.authentication.*;
import org.springframework.security.web.csrf.*;
import org.springframework.stereotype.*;

import java.io.*;

@NullMarked
@RequiredArgsConstructor
@Component
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final ObjectMapper objectMapper;
    private final UserService userService;
    private final SecurityContextHelper securityContextHelper;

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
            userDetails = userService.updateLanguagePreference(userDetails.getUserId(), effectiveLanguage);
            securityContextHelper.refreshSessionPrincipal(userDetails, request);
        }
        // Spring only writes the XSRF-TOKEN cookie when the deferred CSRF token is accessed — do it now so the browser can POST to /api/auth/change-password.
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            csrfToken.getToken();
        }
        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_OK);
        objectMapper.writeValue(response.getWriter(), UserSessionDto.from(userDetails));
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
