package org.pluribourse.edition;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.pluribourse.edition.dto.*;
import org.pluribourse.shared.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.*;

import java.time.*;
import java.util.*;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CurrentEditionIT extends IntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private MockHttpSession adminSession;
    private MockHttpSession volunteerSession;
    private Long editionId;

    @BeforeAll
    void setUpSessions() throws Exception {
        MvcResult adminLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "test_admin")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andReturn();
        adminSession = (MockHttpSession) adminLogin.getRequest().getSession(false);

        // Volunteer login requires an active edition; create one temporarily.
        MvcResult tempEdition = mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Setup Edition\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-03\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        Long tempEditionId = objectMapper.readTree(tempEdition.getResponse().getContentAsString()).get("id").asLong();

        MvcResult volunteerLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "volunteer1")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andReturn();
        volunteerSession = (MockHttpSession) volunteerLogin.getRequest().getSession(false);

        // Remove temp edition so @Order(1) starts with no active edition.
        mockMvc.perform(delete("/api/admin/editions/" + tempEditionId)
                        .session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(1)
    void current_edition_returns_404_when_no_edition_exists() throws Exception {
        mockMvc.perform(get("/api/editions/current").session(adminSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/no-active-edition")));
    }

    @Test
    @Order(2)
    void current_edition_returns_200_with_preparation_phase() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditionDto(null, "Bourse 2026", null, null, null, null, false, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3)))))
                .andExpect(status().isCreated())
                .andReturn();
        editionId = objectMapper.readValue(result.getResponse().getContentAsString(), EditionDto.class).id();

        mockMvc.perform(get("/api/editions/current").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(editionId))
                .andExpect(jsonPath("$.phase").value("PREPARATION"));
    }

    @Test
    @Order(3)
    void current_edition_accessible_by_volunteer() throws Exception {
        mockMvc.perform(get("/api/editions/current").session(volunteerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(editionId))
                .andExpect(jsonPath("$.phase").value("PREPARATION"));
    }

    @Test
    @Order(4)
    void current_edition_returns_404_after_edition_closed() throws Exception {
        mockMvc.perform(put("/api/admin/editions/" + editionId + "/categories")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(
                                Map.of("name", "Jouets", "tableNumbers", List.of(1))))))
                .andExpect(status().isOk());

        // Advance PREPARATION → DEPOSIT → SALE → POST_SALE → CLOSED
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                            .session(adminSession).with(csrf()))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/editions/current").session(adminSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/no-active-edition")));
    }
}
