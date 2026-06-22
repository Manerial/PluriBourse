package org.pluribourse.shared.security.handlers;

import jakarta.servlet.http.*;
import org.jspecify.annotations.*;
import org.springframework.security.access.*;
import org.springframework.security.web.access.*;

import java.io.*;

@NullMarked
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        response.setContentType("application/problem+json");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.getWriter().write(
                "{\"type\":\"https://pluribourse/errors/forbidden\"," +
                        "\"title\":\"Forbidden\",\"status\":403," +
                        "\"detail\":\"Access denied\"}"
        );
    }
}
