package org.pluribourse.user;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.pluribourse.shared.*;
import org.pluribourse.user.entities.*;
import org.pluribourse.user.repositories.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.*;
import org.springframework.security.crypto.password.*;
import org.springframework.test.web.servlet.*;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserManagementIT extends IntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private MockHttpSession adminSession;
    private MockHttpSession volunteerSession;
    private long aliceId;

    @BeforeAll
    void setUpSessions() throws Exception {
        MvcResult adminLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "test_admin")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andReturn();
        adminSession = (MockHttpSession) adminLogin.getRequest().getSession(false);

        // Volunteer login requires an active edition (Story 2.3 gate).
        mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Setup Edition\"}"))
                .andExpect(status().isCreated());

        MvcResult volunteerLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "volunteer1")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andReturn();
        volunteerSession = (MockHttpSession) volunteerLogin.getRequest().getSession(false);
    }

    @Test
    @Order(1)
    void unauthenticated_access_returns_401() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(2)
    void volunteer_cannot_access_admin_endpoint() throws Exception {
        mockMvc.perform(get("/api/admin/users").session(volunteerSession))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(3)
    void admin_creates_volunteer() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/users")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Alice\",\"lastName\":\"Smith\",\"username\":\"alice\",\"password\":\"Password1\",\"role\":\"VOLUNTEER\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/admin/users/")))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.role").value("VOLUNTEER"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andReturn();

        aliceId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        User alice = userRepository.findById(aliceId).orElseThrow();
        assertThat(alice.getRole().name()).isEqualTo("VOLUNTEER");
        assertThat(alice.getEnabled()).isTrue();
        assertThat(alice.isForcePasswordChange()).isTrue();
    }

    @Test
    @Order(4)
    void admin_creates_duplicate_volunteer_returns_409() throws Exception {
        mockMvc.perform(post("/api/admin/users")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Alice\",\"lastName\":\"Smith\",\"username\":\"alice\",\"password\":\"Password1\",\"role\":\"VOLUNTEER\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(5)
    void admin_creates_volunteer_with_blank_first_name_returns_400() throws Exception {
        mockMvc.perform(post("/api/admin/users")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"\",\"lastName\":\"Smith\",\"username\":\"bob\",\"password\":\"Password1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(6)
    void admin_creates_volunteer_with_weak_password_returns_400() throws Exception {
        mockMvc.perform(post("/api/admin/users")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Bob\",\"lastName\":\"Smith\",\"username\":\"bob\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(7)
    void admin_lists_volunteers_includes_alice_but_not_admin_accounts() throws Exception {
        mockMvc.perform(get("/api/admin/users").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].username", hasItem("alice")))
                .andExpect(jsonPath("$[*].username", not(hasItem("Admin"))))
                .andExpect(jsonPath("$[*].username", not(hasItem("test_admin"))));
    }

    @Test
    @Order(8)
    void admin_disables_alice() throws Exception {
        mockMvc.perform(put("/api/admin/users/" + aliceId + "/disable")
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isOk());

        assertThat(userRepository.findById(aliceId).orElseThrow().getEnabled()).isFalse();
    }

    @Test
    @Order(9)
    void admin_cannot_disable_admin_account() throws Exception {
        User adminUser = userRepository.findByUsername("Admin").orElseThrow();

        mockMvc.perform(put("/api/admin/users/" + adminUser.getId() + "/disable")
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @Order(10)
    void admin_enables_alice() throws Exception {
        mockMvc.perform(put("/api/admin/users/" + aliceId + "/enable")
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isOk());

        assertThat(userRepository.findById(aliceId).orElseThrow().getEnabled()).isTrue();
    }

    @Test
    @Order(11)
    void admin_resets_alice_password_and_sets_force_change_flag() throws Exception {
        mockMvc.perform(put("/api/admin/users/" + aliceId + "/reset-password")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"NewPass1!\"}"))
                .andExpect(status().isOk());

        User alice = userRepository.findById(aliceId).orElseThrow();
        assertThat(alice.isForcePasswordChange()).isTrue();
        assertThat(passwordEncoder.matches("NewPass1!", alice.getPassword())).isTrue();
    }

    @Test
    @Order(12)
    void admin_cannot_reset_admin_account_password() throws Exception {
        User adminUser = userRepository.findByUsername("Admin").orElseThrow();

        mockMvc.perform(put("/api/admin/users/" + adminUser.getId() + "/reset-password")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"NewPass1!\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @Order(13)
    void admin_disable_nonexistent_user_returns_404() throws Exception {
        mockMvc.perform(put("/api/admin/users/99999/disable")
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(14)
    void admin_enable_nonexistent_user_returns_404() throws Exception {
        mockMvc.perform(put("/api/admin/users/99999/enable")
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(15)
    void admin_reset_password_nonexistent_user_returns_404() throws Exception {
        mockMvc.perform(put("/api/admin/users/99999/reset-password")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"NewPass1!\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(16)
    void admin_reset_password_with_short_password_returns_400() throws Exception {
        mockMvc.perform(put("/api/admin/users/" + aliceId + "/reset-password")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(17)
    void volunteer_can_change_own_password() throws Exception {
        mockMvc.perform(post("/api/auth/change-password")
                        .session(volunteerSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"VolPass1!\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(18)
    void disabled_user_cannot_log_in() throws Exception {
        mockMvc.perform(put("/api/admin/users/" + aliceId + "/disable")
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "alice")
                        .param("password", "NewPass1!"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://pluribourse/errors/account-disabled"));
    }

    @Test
    @Order(19)
    void admin_deletes_alice_returns_204() throws Exception {
        mockMvc.perform(delete("/api/admin/users/" + aliceId)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(aliceId)).isEmpty();
    }

    @Test
    @Order(20)
    void alice_no_longer_appears_in_list() throws Exception {
        mockMvc.perform(get("/api/admin/users").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].username", not(hasItem("alice"))));
    }

    @Test
    @Order(21)
    void admin_cannot_delete_admin_account() throws Exception {
        User adminUser = userRepository.findByUsername("Admin").orElseThrow();
        mockMvc.perform(delete("/api/admin/users/" + adminUser.getId())
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @Order(22)
    void admin_delete_nonexistent_user_returns_404() throws Exception {
        mockMvc.perform(delete("/api/admin/users/99999")
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }
}
