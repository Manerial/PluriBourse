package org.pluribourse.shared;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class SecurityConfigIT {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void unauthenticated_request_returns_401_not_redirect() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void health_endpoint_is_public() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "VOLUNTEER")
    void volunteer_cannot_access_admin_endpoint() throws Exception {
        mockMvc.perform(get("/api/admin/anything"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_gets_past_security_on_admin_endpoint() throws Exception {
        // ADMIN bypasses the 403, endpoint returns 404 because no controller exists yet
        mockMvc.perform(get("/api/admin/anything"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403));
    }

    @Test
    @WithMockUser(roles = "SELLER")
    void seller_is_blocked_on_any_endpoint() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    void login_with_wrong_credentials_returns_401() throws Exception {
        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "Admin")
                        .param("password", "wrongpassword"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_with_correct_credentials_returns_200_with_json() throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "Admin")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"forcePasswordChange\":true");
        assertThat(body).contains("\"role\":\"ADMIN\"");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void logout_returns_200() throws Exception {
        mockMvc.perform(post("/logout").with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void ac3_real_login_creates_spring_session_and_subsequent_request_succeeds() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "Admin")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andReturn();

        // MockMvc holds the session object on the request; reuse it for subsequent calls
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk());
    }
}
