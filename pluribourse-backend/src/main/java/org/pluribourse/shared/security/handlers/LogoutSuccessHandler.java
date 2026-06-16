package org.pluribourse.shared.security.handlers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class LogoutSuccessHandler
        implements org.springframework.security.web.authentication.logout.LogoutSuccessHandler {

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
    }
}
