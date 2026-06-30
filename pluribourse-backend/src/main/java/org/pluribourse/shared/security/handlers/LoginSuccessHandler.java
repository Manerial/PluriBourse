package org.pluribourse.shared.security.handlers;

import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.*;
import org.jspecify.annotations.*;
import org.pluribourse.edition.entity.*;
import org.pluribourse.edition.repository.*;
import org.pluribourse.shared.security.*;
import org.pluribourse.user.dtos.*;
import org.pluribourse.user.entities.*;
import org.pluribourse.user.enums.*;
import org.pluribourse.user.services.*;
import org.springframework.http.*;
import org.springframework.security.core.*;
import org.springframework.security.core.context.*;
import org.springframework.security.web.authentication.*;
import org.springframework.security.web.csrf.*;
import org.springframework.stereotype.*;

import java.io.*;
import java.net.*;
import java.util.*;

@NullMarked
@Component
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final List<PhaseType> ACTIVE_PHASES = List.of(
            PhaseType.PREPARATION, PhaseType.DEPOSIT, PhaseType.SALE, PhaseType.POST_SALE
    );

    private final ObjectMapper objectMapper;
    private final UserService userService;
    private final SecurityContextHelper securityContextHelper;
    private final EditionRepository editionRepository;

    public LoginSuccessHandler(ObjectMapper objectMapper, UserService userService, SecurityContextHelper securityContextHelper, EditionRepository editionRepository) {
        this.objectMapper = objectMapper;
        this.userService = userService;
        this.securityContextHelper = securityContextHelper;
        this.editionRepository = editionRepository;
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
        if (Role.VOLUNTEER.name().equals(userDetails.getRole()) && !editionRepository.existsByPhaseIn(ACTIVE_PHASES)) {
            SecurityContextHolder.clearContext();
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                    HttpStatus.UNAUTHORIZED, "No edition is currently active");
            pd.setType(URI.create("https://pluribourse/errors/no-active-edition"));
            pd.setTitle("No Active Edition");
            response.setContentType("application/problem+json");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            objectMapper.writeValue(response.getWriter(), pd);
            return;
        }
        Language effectiveLanguage = userDetails.getPreferredLanguage();
        if (!userDetails.isLanguageInitialized()) {
            effectiveLanguage = detectLanguage(request);
            userDetails = userService.updateLanguagePreference(userDetails.getUserId(), effectiveLanguage);
            securityContextHelper.refreshSessionPrincipal(userDetails, request);
        }
        // Force the CSRF cookie to be written before the response body is committed.
        // CsrfFilter loads a deferred token for all requests (including CSRF-ignored ones like /api/auth/login),
        // but the cookie is only written when the token is actually accessed. Without this, the browser
        // has no XSRF-TOKEN cookie after login and the next POST (/api/auth/change-password) fails CSRF.
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            csrfToken.getToken();
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
