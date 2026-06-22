package org.pluribourse.shared.security.handlers;

import jakarta.servlet.http.*;
import org.jspecify.annotations.*;
import org.springframework.security.core.*;
import org.springframework.stereotype.*;

@NullMarked
@Component
public class LogoutSuccessHandler
        implements org.springframework.security.web.authentication.logout.LogoutSuccessHandler {

    @Override
    public void onLogoutSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            @Nullable Authentication authentication) {
        response.setStatus(HttpServletResponse.SC_OK);
    }
}
