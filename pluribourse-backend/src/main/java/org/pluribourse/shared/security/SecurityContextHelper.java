package org.pluribourse.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.pluribourse.user.entities.PluriBourseUserDetails;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

@Component
public class SecurityContextHelper {

    public void refreshSessionPrincipal(PluriBourseUserDetails newDetails, HttpServletRequest request) {
        UsernamePasswordAuthenticationToken newAuth = UsernamePasswordAuthenticationToken.authenticated(
                newDetails, null, newDetails.getAuthorities());
        SecurityContext context = SecurityContextHolder.getContext();
        context.setAuthentication(newAuth);
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        }
    }
}
