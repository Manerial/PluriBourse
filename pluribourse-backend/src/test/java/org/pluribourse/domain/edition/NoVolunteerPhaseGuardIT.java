package org.pluribourse.domain.edition;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.pluribourse.domain.edition.dto.EditionCategoryDto;
import org.pluribourse.domain.edition.dto.EditionDto;
import org.pluribourse.domain.user.dtos.UserDto;
import org.pluribourse.shared.IntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.endsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PREPARATION → DEPOSIT must be blocked once no volunteer account exists, mirroring the existing
 * "no categories configured" guard (PhaseTransitionIT): a Dépôt phase with nobody able to log in
 * and register sellers/articles is a dead end for the association. Deletes the seeded
 * volunteer1/volunteer2 (test-data.sql) rather than reusing {@code PhaseTransitionIT}, whose
 * later orders (SSE) depend on a live {@code volunteerSession} staying valid throughout.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NoVolunteerPhaseGuardIT extends IntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private MockHttpSession adminSession;
    private Long editionId;

    @Test
    @Order(1)
    void admin_login_and_delete_every_seeded_volunteer() throws Exception {
        MvcResult adminLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "test_admin")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andReturn();
        adminSession = (MockHttpSession) adminLogin.getRequest().getSession(false);

        MvcResult listResult = mockMvc.perform(get("/api/admin/users").session(adminSession))
                .andExpect(status().isOk())
                .andReturn();
        List<UserDto> volunteers = objectMapper.readValue(
                listResult.getResponse().getContentAsString(), new TypeReference<List<UserDto>>() {
                });
        for (UserDto volunteer : volunteers) {
            mockMvc.perform(delete("/api/admin/users/" + volunteer.id())
                            .session(adminSession).with(csrf()))
                    .andExpect(status().isNoContent());
        }

        mockMvc.perform(get("/api/admin/users").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @Order(2)
    void create_edition_with_categories_but_no_volunteer() throws Exception {
        MvcResult editionResult = mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditionDto(null, "Bourse Sans Bénévole", null, null, null, null, false, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3), null))))
                .andExpect(status().isCreated())
                .andReturn();
        editionId = objectMapper.readValue(editionResult.getResponse().getContentAsString(), EditionDto.class).id();

        mockMvc.perform(put("/api/admin/editions/" + editionId + "/categories")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(new EditionCategoryDto(null, "Jouets", List.of(1))))))
                .andExpect(status().isOk());
    }

    @Test
    @Order(3)
    void advance_to_deposit_without_any_volunteer_returns_422() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(endsWith("/no-volunteer-configured")));

        mockMvc.perform(get("/api/admin/editions/" + editionId).session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("PREPARATION"));
    }

    @Test
    @Order(4)
    void advance_to_deposit_succeeds_once_a_volunteer_exists() throws Exception {
        mockMvc.perform(post("/api/admin/users")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Alice\",\"lastName\":\"Smith\",\"username\":\"alice.novolunteer\",\"password\":\"Password1\",\"role\":\"VOLUNTEER\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("DEPOSIT"));
    }
}
