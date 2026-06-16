package org.pluribourse.shared.security.handlers;

import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.*;
import org.pluribourse.user.dtos.*;
import org.pluribourse.user.entities.*;
import org.springframework.security.core.*;
import org.springframework.security.web.authentication.*;
import org.springframework.stereotype.*;

import java.io.*;

@Component
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final ObjectMapper objectMapper;

    public LoginSuccessHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        PluriBourseUserDetails userDetails = (PluriBourseUserDetails) authentication.getPrincipal();
        var dto = new UserSessionDto(
                userDetails.getUsername(),
                userDetails.getRole(),
                userDetails.isForcePasswordChange()
        );
        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_OK);
        objectMapper.writeValue(response.getWriter(), dto);
    }
}
