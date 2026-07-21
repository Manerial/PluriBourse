package org.pluribourse.domain.user;

import org.junit.jupiter.api.*;
import org.pluribourse.domain.user.repositories.*;
import org.pluribourse.shared.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * E2E scenario: Admin logs in with initial credentials (forcePasswordChange=true),
 * gets blocked on protected endpoints, changes their password, then regains full access.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PasswordChangeFlowIT extends IntegrationTest {

    @Autowired
    private UserRepository userRepository;

    private MockHttpSession session;

    @Test
    @Order(1)
    void login_with_wrong_credentials_returns_401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "Admin")
                        .param("password", "wrongpassword"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(2)
    void admin_logs_in_with_initial_credentials() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "Admin")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.forcePasswordChange").value(true))
                .andReturn();

        session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
    }

    @Test
    @Order(3)
    void admin_is_blocked_on_protected_endpoints_before_password_change() throws Exception {
        mockMvc.perform(get("/api/admin/users").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://pluribourse/errors/password-change-required"));
    }

    @Test
    @Order(4)
    void admin_can_read_own_session_info_despite_force_change() throws Exception {
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.forcePasswordChange").value(true));
    }

    @Test
    @Order(5)
    void change_password_without_authentication_returns_401() throws Exception {
        mockMvc.perform(post("/api/auth/change-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"NewSecurePass1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(6)
    void change_password_without_uppercase_returns_400() throws Exception {
        mockMvc.perform(post("/api/auth/change-password")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"alllowercase1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(7)
    void change_password_without_digit_returns_400() throws Exception {
        mockMvc.perform(post("/api/auth/change-password")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"AllUppercaseNoDigit\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(8)
    void admin_changes_password() throws Exception {
        mockMvc.perform(post("/api/auth/change-password")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"NewSecurePass1\"}"))
                .andExpect(status().isOk());

        assertThat(userRepository.findByUsername("Admin").orElseThrow().isForcePasswordChange()).isFalse();
    }

    @Test
    @Order(9)
    void admin_can_now_access_protected_endpoints() throws Exception {
        mockMvc.perform(get("/api/admin/users").session(session))
                .andExpect(status().isOk());
    }

    @Test
    @Order(10)
    void logout_returns_200_and_clears_session() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @Order(11)
    void session_is_unusable_after_logout() throws Exception {
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }
}
