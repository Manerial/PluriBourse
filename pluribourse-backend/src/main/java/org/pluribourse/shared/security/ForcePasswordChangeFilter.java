package org.pluribourse.shared.security;

import com.fasterxml.jackson.databind.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.*;
import org.jspecify.annotations.*;
import org.pluribourse.user.entities.*;
import org.springframework.http.*;
import org.springframework.security.core.*;
import org.springframework.security.core.context.*;
import org.springframework.web.filter.*;

import java.io.*;
import java.net.*;
import java.util.Set;

@NullMarked
@RequiredArgsConstructor
public class ForcePasswordChangeFilter extends OncePerRequestFilter {

    private static final Set<String> EXEMPT_PATHS =
            // getRequestURI() returns the raw client-facing path, including the /api context path — unlike the
            // Spring Security request matchers above, which are already context-path-relative.
            Set.of("/api/auth/change-password", "/api/auth/me", "/api/auth/logout", "/api/auth/login", "/api/actuator/health",
                    "/api/sse/events", "/api/editions/current");

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() &&
                auth.getPrincipal() instanceof PluriBourseUserDetails ud) {
            if (ud.isForcePasswordChange() &&
                    !EXEMPT_PATHS.contains(request.getRequestURI())) {
                ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                        org.springframework.http.HttpStatus.FORBIDDEN,
                        "You must change your password before accessing this resource");
                pd.setType(URI.create("https://pluribourse/errors/password-change-required"));
                pd.setTitle("Password Change Required");
                response.setContentType("application/problem+json");
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                objectMapper.writeValue(response.getWriter(), pd);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
