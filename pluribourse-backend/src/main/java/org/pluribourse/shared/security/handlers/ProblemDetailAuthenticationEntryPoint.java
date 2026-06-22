package org.pluribourse.shared.security.handlers;

import jakarta.servlet.http.*;
import org.jspecify.annotations.*;
import org.springframework.security.core.*;
import org.springframework.security.web.*;

import java.io.*;

@NullMarked
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        response.setContentType("application/problem+json");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write(
                "{\"type\":\"https://pluribourse/errors/unauthorized\"," +
                        "\"title\":\"Unauthorized\",\"status\":401," +
                        "\"detail\":\"Authentication required\"}"
        );
    }
}
