package org.pluribourse.user;

import org.junit.jupiter.api.*;
import org.pluribourse.user.entities.*;
import org.pluribourse.user.enums.*;
import org.pluribourse.user.repositories.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.*;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.*;
import org.springframework.transaction.annotation.*;
import org.springframework.web.context.*;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class UserControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    // ── GET /api/admin/users ──────────────────────────────────────────────────

    @Test
    void listVolunteers_returns_200_with_volunteer_list() throws Exception {
        createVolunteer("alice", "Alice", "Smith");

        mockMvc.perform(get("/api/admin/users")
                        .with(user("Admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].role").value("VOLUNTEER"));
    }

    @Test
    void listVolunteers_returns_401_when_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listVolunteers_returns_403_for_volunteer_role() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .with(user("alice").roles("VOLUNTEER")))
                .andExpect(status().isForbidden());
    }

    // ── POST /api/admin/users ─────────────────────────────────────────────────

    @Test
    void createVolunteer_with_valid_body_returns_201() throws Exception {
        mockMvc.perform(post("/api/admin/users")
                        .with(user("Admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Bob\",\"lastName\":\"Dupont\",\"username\":\"bob\",\"password\":\"Password1\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/admin/users/")))
                .andExpect(jsonPath("$.username").value("bob"))
                .andExpect(jsonPath("$.role").value("VOLUNTEER"))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void createVolunteer_with_blank_firstName_returns_400() throws Exception {
        mockMvc.perform(post("/api/admin/users")
                        .with(user("Admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"\",\"lastName\":\"Dupont\",\"username\":\"bob\",\"password\":\"Password1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createVolunteer_with_short_password_returns_400() throws Exception {
        mockMvc.perform(post("/api/admin/users")
                        .with(user("Admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Bob\",\"lastName\":\"Dupont\",\"username\":\"bob2\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createVolunteer_with_duplicate_username_returns_409() throws Exception {
        createVolunteer("charlie", "Charlie", "Martin");

        mockMvc.perform(post("/api/admin/users")
                        .with(user("Admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Charlie\",\"lastName\":\"Martin\",\"username\":\"charlie\",\"password\":\"Password1\"}"))
                .andExpect(status().isConflict());
    }

    // ── PUT /api/admin/users/{id}/disable ─────────────────────────────────────

    @Test
    void disable_volunteer_returns_200() throws Exception {
        var volunteer = createVolunteer("dave", "Dave", "Bernard");

        mockMvc.perform(put("/api/admin/users/" + volunteer.getId() + "/disable")
                        .with(user("Admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk());

        var updated = userRepository.findById(volunteer.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(updated.getEnabled()).isFalse();
    }

    @Test
    void disable_admin_account_returns_403() throws Exception {
        var admin = userRepository.findByUsername("Admin").orElseThrow();

        mockMvc.perform(put("/api/admin/users/" + admin.getId() + "/disable")
                        .with(user("Admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ── PUT /api/admin/users/{id}/reset-password ──────────────────────────────

    @Test
    void reset_password_volunteer_returns_200_and_sets_force_change() throws Exception {
        var volunteer = createVolunteer("eve", "Eve", "Leclerc");

        mockMvc.perform(put("/api/admin/users/" + volunteer.getId() + "/reset-password")
                        .with(user("Admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"NewPass1!\"}"))
                .andExpect(status().isOk());

        var updated = userRepository.findById(volunteer.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(updated.isForcePasswordChange()).isTrue();
    }

    @Test
    void reset_password_admin_account_returns_403() throws Exception {
        var admin = userRepository.findByUsername("Admin").orElseThrow();

        mockMvc.perform(put("/api/admin/users/" + admin.getId() + "/reset-password")
                        .with(user("Admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"NewPass1!\"}"))
                .andExpect(status().isForbidden());
    }

    // ── PUT /api/admin/users/{id}/enable ──────────────────────────────────────

    @Test
    void enable_volunteer_returns_200() throws Exception {
        var volunteer = createVolunteer("frank", "Frank", "Morel");
        volunteer.setEnabled(false);
        userRepository.save(volunteer);

        mockMvc.perform(put("/api/admin/users/" + volunteer.getId() + "/enable")
                        .with(user("Admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk());

        var updated = userRepository.findById(volunteer.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(updated.getEnabled()).isTrue();
    }

    @Test
    void enable_admin_account_returns_403() throws Exception {
        var admin = userRepository.findByUsername("Admin").orElseThrow();

        mockMvc.perform(put("/api/admin/users/" + admin.getId() + "/enable")
                        .with(user("Admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private User createVolunteer(String username, String firstName, String lastName) {
        var user = new User();
        user.setUsername(username);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setPassword(passwordEncoder.encode("Password1"));
        user.setRole(Role.VOLUNTEER);
        user.setPreferredLanguage(Language.FR);
        user.setEnabled(true);
        user.setForcePasswordChange(false);
        return userRepository.save(user);
    }
}
