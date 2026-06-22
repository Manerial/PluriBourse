package org.pluribourse.shared.security.handlers;

import jakarta.servlet.http.*;
import org.jspecify.annotations.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.security.web.authentication.*;
import org.springframework.stereotype.*;

import java.io.*;

@NullMarked
@Component
public class LoginFailureHandler implements AuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        response.setContentType("application/problem+json");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        if (exception instanceof DisabledException) {
            response.getWriter().write(
                    "{\"type\":\"https://pluribourse/errors/account-disabled\"," +
                            "\"title\":\"Account Disabled\",\"status\":401," +
                            "\"detail\":\"This account has been disabled\"}"
            );
        } else {
            response.getWriter().write(
                    "{\"type\":\"https://pluribourse/errors/authentication-failed\"," +
                            "\"title\":\"Authentication Failed\",\"status\":401," +
                            "\"detail\":\"Invalid username or password\"}"
            );
        }
    }
}
