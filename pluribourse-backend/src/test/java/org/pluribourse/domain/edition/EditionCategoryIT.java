package org.pluribourse.domain.edition;

import com.fasterxml.jackson.core.type.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.pluribourse.domain.edition.dto.*;
import org.pluribourse.domain.edition.entity.*;
import org.pluribourse.domain.edition.repository.*;
import org.pluribourse.shared.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.*;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EditionCategoryIT extends IntegrationTest {

    @Autowired
    private EditionRepository editionRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private MockHttpSession adminSession;
    private MockHttpSession volunteerSession;
    private Long editionId;
    private Long sourceEditionId;

    private static final String BASE = "/api/admin/editions/";

    @BeforeAll
    void setUpSessions() throws Exception {
        MvcResult adminLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "test_admin")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andReturn();
        adminSession = (MockHttpSession) adminLogin.getRequest().getSession(false);

        // Create source edition and add categories before closing it
        MvcResult sourceResult = mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditionDto(null, "Source Edition", null, null, null, null, false, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3), null))))
                .andExpect(status().isCreated())
                .andReturn();
        sourceEditionId = objectMapper.readValue(sourceResult.getResponse().getContentAsString(), EditionDto.class).id();

        List<EditionCategoryDto> sourceCats = List.of(
                new EditionCategoryDto(null, "Jouets", List.of(5, 6)),
                new EditionCategoryDto(null, "Livres", List.of(7, 8))
        );
        mockMvc.perform(put(BASE + sourceEditionId + "/categories")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sourceCats)))
                .andExpect(status().isOk());

        // Force source edition to CLOSED so it no longer blocks new edition creation
        Edition source = editionRepository.findById(sourceEditionId).orElseThrow();
        source.setPhase(PhaseType.CLOSED);
        editionRepository.save(source);

        // Create temp edition so volunteer can log in (volunteer gate requires an active edition)
        MvcResult tempResult = mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditionDto(null, "Temp Edition", null, null, null, null, false, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3), null))))
                .andExpect(status().isCreated())
                .andReturn();
        Long tempEditionId = objectMapper.readValue(tempResult.getResponse().getContentAsString(), EditionDto.class).id();

        MvcResult volunteerLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "volunteer1")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andReturn();
        volunteerSession = (MockHttpSession) volunteerLogin.getRequest().getSession(false);

        mockMvc.perform(delete("/api/admin/editions/" + tempEditionId)
                        .session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(1)
    void admin_creates_edition_in_preparation() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditionDto(null, "Bourse Categories 2026", null, null, null, null, false, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3), null))))
                .andExpect(status().isCreated())
                .andReturn();
        editionId = objectMapper.readValue(result.getResponse().getContentAsString(), EditionDto.class).id();
        assertThat(editionId).isNotNull();
    }

    @Test
    @Order(2)
    void get_categories_returns_empty_list_initially() throws Exception {
        mockMvc.perform(get(BASE + editionId + "/categories").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @Order(3)
    void put_one_category_saves_and_returns_it() throws Exception {
        List<EditionCategoryDto> payload = List.of(new EditionCategoryDto(null, "Jouets", List.of(1, 2)));
        MvcResult result = mockMvc.perform(put(BASE + editionId + "/categories")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn();
        List<EditionCategoryDto> returned = objectMapper.readValue(
                result.getResponse().getContentAsString(), new TypeReference<>() {
                });
        assertThat(returned).hasSize(1);
        assertThat(returned.getFirst().name()).isEqualTo("Jouets");
        assertThat(returned.getFirst().tableNumbers()).containsExactly(1, 2);
    }

    @Test
    @Order(4)
    void get_categories_returns_persisted_category() throws Exception {
        MvcResult result = mockMvc.perform(get(BASE + editionId + "/categories").session(adminSession))
                .andExpect(status().isOk())
                .andReturn();
        List<EditionCategoryDto> list = objectMapper.readValue(
                result.getResponse().getContentAsString(), new TypeReference<>() {
                });
        assertThat(list).hasSize(1);
        assertThat(list.getFirst().name()).isEqualTo("Jouets");
    }

    @Test
    @Order(5)
    void put_two_categories_with_shared_table_succeeds() throws Exception {
        List<EditionCategoryDto> payload = List.of(
                new EditionCategoryDto(null, "Jouets", List.of(1, 2)),
                new EditionCategoryDto(null, "Livres", List.of(2, 3))
        );
        MvcResult result = mockMvc.perform(put(BASE + editionId + "/categories")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn();
        List<EditionCategoryDto> returned = objectMapper.readValue(
                result.getResponse().getContentAsString(), new TypeReference<>() {
                });
        assertThat(returned).hasSize(2);
    }

    @Test
    @Order(6)
    void put_category_without_tables_returns_400() throws Exception {
        List<EditionCategoryDto> payload = List.of(new EditionCategoryDto(null, "BD", List.of()));
        mockMvc.perform(put(BASE + editionId + "/categories")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(7)
    void put_category_with_blank_name_returns_400() throws Exception {
        List<EditionCategoryDto> payload = List.of(new EditionCategoryDto(null, "  ", List.of(1)));
        mockMvc.perform(put(BASE + editionId + "/categories")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(8)
    void put_categories_locked_in_deposit_phase() throws Exception {
        // Advance to DEPOSIT
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("DEPOSIT"));

        List<EditionCategoryDto> payload = List.of(new EditionCategoryDto(null, "Jouets", List.of(1)));
        mockMvc.perform(put(BASE + editionId + "/categories")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/categories-locked")));
    }

    @Test
    @Order(9)
    void get_categories_readable_in_deposit_phase() throws Exception {
        mockMvc.perform(get(BASE + editionId + "/categories").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @Order(10)
    void rollback_to_preparation_unlocks_categories() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/rollback")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("PREPARATION"));

        List<EditionCategoryDto> payload = List.of(new EditionCategoryDto(null, "Jouets", List.of(1, 3)));
        mockMvc.perform(put(BASE + editionId + "/categories")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @Order(11)
    void copy_from_closed_edition_replaces_categories() throws Exception {
        MvcResult result = mockMvc.perform(post(BASE + editionId + "/categories/copy-from/" + sourceEditionId)
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        List<EditionCategoryDto> copied = objectMapper.readValue(
                result.getResponse().getContentAsString(), new TypeReference<>() {
                });
        assertThat(copied).hasSize(2);
        assertThat(copied).extracting(EditionCategoryDto::name)
                .containsExactlyInAnyOrder("Jouets", "Livres");
    }

    @Test
    @Order(12)
    void volunteer_get_categories_returns_403() throws Exception {
        mockMvc.perform(get(BASE + editionId + "/categories").session(volunteerSession))
                .andExpect(status().isForbidden());
    }
}
