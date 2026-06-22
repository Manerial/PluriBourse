package org.pluribourse.shared;

import org.junit.jupiter.api.*;
import org.pluribourse.shared.security.handlers.*;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.*;

import static org.assertj.core.api.Assertions.*;

class LoginFailureHandlerTest {

    private final LoginFailureHandler handler = new LoginFailureHandler();

    @Test
    void disabled_exception_returns_account_disabled_error() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, new DisabledException("disabled"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        assertThat(response.getContentAsString()).contains("account-disabled");
    }

    @Test
    void bad_credentials_returns_authentication_failed_error() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, new BadCredentialsException("bad"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("authentication-failed");
    }
}
