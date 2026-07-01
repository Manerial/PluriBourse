package org.pluribourse.edition;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.pluribourse.edition.dto.*;
import org.pluribourse.edition.entity.*;
import org.pluribourse.edition.repository.*;
import org.pluribourse.shared.*;
import org.pluribourse.user.enums.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.*;

import java.math.*;
import java.time.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EditionManagementIT extends IntegrationTest {

    @Autowired
    private EditionRepository repository;
    @Autowired
    private ObjectMapper objectMapper;

    private MockHttpSession adminSession;
    private MockHttpSession volunteerSession;
    private Long createdEditionId;

    @BeforeAll
    void setUpSessions() throws Exception {
        MvcResult adminLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "test_admin")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andReturn();
        adminSession = (MockHttpSession) adminLogin.getRequest().getSession(false);

        // Volunteer login requires an active edition (Story 2.3 gate); create one temporarily.
        MvcResult tempEdition = mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Setup Edition\"}"))
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

        // Remove the temporary edition so @Order(3) sees an empty list.
        // The volunteer session remains valid (Story 2.3 AC5 — session invalidation is out of scope).
        mockMvc.perform(delete("/api/admin/editions/" + tempEditionId)
                        .session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());
    }

    // Tests ordered as a story-board (state persists between methods via PER_CLASS + no @Transactional):

    @Test
    @Order(1)
    void unauthenticated_get_returns_401() throws Exception {
        mockMvc.perform(get("/api/admin/editions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(2)
    void volunteer_get_returns_403() throws Exception {
        mockMvc.perform(get("/api/admin/editions").session(volunteerSession))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(3)
    void admin_get_returns_empty_list() throws Exception {
        mockMvc.perform(get("/api/admin/editions").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @Order(4)
    void admin_create_edition_without_rate_and_language_uses_instance_defaults() throws Exception {
        EditionDto dto = new EditionDto(null, "Bourse 2026", null, null, null, null, false, null, null);
        MvcResult result = mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();
        EditionDto created = objectMapper.readValue(result.getResponse().getContentAsString(), EditionDto.class);
        createdEditionId = created.id();
        assertThat(createdEditionId).isNotNull();
        assertThat(created.name()).isEqualTo("Bourse 2026");
        assertThat(created.phase()).isEqualTo(PhaseType.PREPARATION);
        assertThat(created.commissionRate()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(created.documentLanguage()).isEqualTo(Language.EN);
    }

    @Test
    @Order(5)
    void admin_create_second_edition_while_active_returns_422() throws Exception {
        EditionDto dto = new EditionDto(null, "Bourse 2027", null, null, null, null, false, null, null);
        mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @Order(6)
    void admin_get_returns_one_edition() throws Exception {
        mockMvc.perform(get("/api/admin/editions").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Bourse 2026"));
    }

    @Test
    @Order(7)
    void admin_update_edition_in_preparation_succeeds() throws Exception {
        EditionDto dto = new EditionDto(null, "Bourse 2026 Modifiée", null, new BigDecimal("15.00"), Language.FR, null, false, null, null);
        mockMvc.perform(put("/api/admin/editions/" + createdEditionId)
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Bourse 2026 Modifiée"))
                .andExpect(jsonPath("$.commissionRate").value(15.00))
                .andExpect(jsonPath("$.documentLanguage").value("FR"));

        Edition edition = repository.findById(createdEditionId).orElseThrow();
        assertThat(edition.getCommissionRate()).isEqualByComparingTo(new BigDecimal("15.00"));
    }

    @Test
    @Order(8)
    void admin_update_commission_rate_frozen_in_deposit_phase_returns_422() throws Exception {
        Edition edition = repository.findById(createdEditionId).orElseThrow();
        edition.setPhase(PhaseType.DEPOSIT);
        repository.save(edition);

        EditionDto dto = new EditionDto(null, "Bourse 2026 Modifiée", null, new BigDecimal("30.00"), Language.FR, null, false, null, null);
        mockMvc.perform(put("/api/admin/editions/" + createdEditionId)
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @Order(9)
    void admin_update_name_and_language_in_deposit_phase_succeeds() throws Exception {
        // Edition is still in DEPOSIT from Order 8 — send null commissionRate to skip the frozen-rate check
        EditionDto dto = new EditionDto(null, "Bourse 2026 Finale", null, null, Language.EN, null, false, null, null);
        mockMvc.perform(put("/api/admin/editions/" + createdEditionId)
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Bourse 2026 Finale"))
                .andExpect(jsonPath("$.documentLanguage").value("EN"));
    }

    @Test
    @Order(10)
    void admin_delete_in_deposit_phase_returns_422() throws Exception {
        mockMvc.perform(delete("/api/admin/editions/" + createdEditionId)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @Order(11)
    void admin_delete_in_preparation_phase_succeeds() throws Exception {
        Edition edition = repository.findById(createdEditionId).orElseThrow();
        edition.setPhase(PhaseType.PREPARATION);
        repository.save(edition);

        mockMvc.perform(delete("/api/admin/editions/" + createdEditionId)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(12)
    void list_is_empty_after_delete() throws Exception {
        mockMvc.perform(get("/api/admin/editions").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @Order(13)
    void create_edition_with_blank_name_returns_400_rfc7807() throws Exception {
        // AC9: epic says 422, but GlobalExceptionHandler.handleMethodArgumentNotValid returns 400
        // for @Valid DTO violations — do NOT change the handler; test for 400
        String body = "{\"name\":\"\"}";
        mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://pluribourse/errors/validation-failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").isString());
    }

    @Test
    @Order(14)
    void create_edition_with_null_name_returns_400_rfc7807() throws Exception {
        String body = "{\"name\":null}";
        mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://pluribourse/errors/validation-failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").isString());
    }

    @Test
    @Order(15)
    void create_edition_after_closed_edition_succeeds_with_explicit_rate_and_language() throws Exception {
        // CLOSED phase is not active — creating after a CLOSED edition is allowed (FR-010)
        EditionDto dto1 = new EditionDto(null, "Bourse Clôturée", null, null, null, null, false, null, null);
        MvcResult r1 = mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto1)))
                .andExpect(status().isCreated()).andReturn();
        Long id1 = objectMapper.readValue(r1.getResponse().getContentAsString(), EditionDto.class).id();

        Edition edition = repository.findById(id1).orElseThrow();
        edition.setPhase(PhaseType.CLOSED);
        repository.save(edition);

        // Create new edition with explicit rate and language
        EditionDto dto2 = new EditionDto(null, "Bourse Suivante", null, new BigDecimal("12.50"), Language.FR, null, false, null, null);
        MvcResult r2 = mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phase").value("PREPARATION"))
                .andExpect(jsonPath("$.commissionRate").value(12.50))
                .andExpect(jsonPath("$.documentLanguage").value("FR"))
                .andReturn();
        createdEditionId = objectMapper.readValue(r2.getResponse().getContentAsString(), EditionDto.class).id();
    }

    @Test
    @Order(16)
    void admin_get_edition_by_id_returns_edition() throws Exception {
        mockMvc.perform(get("/api/admin/editions/" + createdEditionId).session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdEditionId))
                .andExpect(jsonPath("$.name").value("Bourse Suivante"));
    }

    @Test
    @Order(17)
    void admin_get_edition_by_unknown_id_returns_404() throws Exception {
        mockMvc.perform(get("/api/admin/editions/99999").session(adminSession))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(18)
    void create_edition_with_start_and_end_dates_persists_both() throws Exception {
        // Advance the edition from Order 15 (Bourse Suivante) to CLOSED so it is no longer active
        Edition existing = repository.findById(createdEditionId).orElseThrow();
        existing.setPhase(PhaseType.CLOSED);
        repository.save(existing);

        EditionDto dto = new EditionDto(null, "Bourse Datée", null, null, null, null, false,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3));
        MvcResult result = mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startDate").value("2026-10-01"))
                .andExpect(jsonPath("$.endDate").value("2026-10-03"))
                .andReturn();
        createdEditionId = objectMapper.readValue(result.getResponse().getContentAsString(), EditionDto.class).id();
    }

    @Test
    @Order(19)
    void update_edition_clears_start_date_when_null_sent() throws Exception {
        EditionDto dto = new EditionDto(
                null,
                "Bourse Datée",
                null,
                null,
                null,
                null,
                false,
                null,
                LocalDate.of(2026, 10, 3));
        mockMvc.perform(put("/api/admin/editions/" + createdEditionId)
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startDate").value((Object) null))
                .andExpect(jsonPath("$.endDate").value("2026-10-03"));

        Edition edition = repository.findById(createdEditionId).orElseThrow();
        assertThat(edition.getStartDate()).isNull();
        assertThat(edition.getEndDate()).isEqualTo(LocalDate.of(2026, 10, 3));
    }

    // AC7/AC8 were only exercised against DEPOSIT (Order 8/10). Story 2.2 has since shipped
    // phase transitions, so SALE/POST_SALE/CLOSED are covered here the same way.

    @Test
    @Order(20)
    void admin_update_commission_rate_frozen_in_sale_phase_returns_422() throws Exception {
        Edition edition = repository.findById(createdEditionId).orElseThrow();
        edition.setPhase(PhaseType.SALE);
        repository.save(edition);

        EditionDto dto = new EditionDto(null, "Bourse Datée", null, new BigDecimal("30.00"), null, null, false, null, null);
        mockMvc.perform(put("/api/admin/editions/" + createdEditionId)
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @Order(21)
    void admin_delete_in_sale_phase_returns_422() throws Exception {
        mockMvc.perform(delete("/api/admin/editions/" + createdEditionId)
                        .session(adminSession).with(csrf()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @Order(22)
    void admin_update_commission_rate_frozen_in_post_sale_phase_returns_422() throws Exception {
        Edition edition = repository.findById(createdEditionId).orElseThrow();
        edition.setPhase(PhaseType.POST_SALE);
        repository.save(edition);

        EditionDto dto = new EditionDto(null, "Bourse Datée", null, new BigDecimal("30.00"), null, null, false, null, null);
        mockMvc.perform(put("/api/admin/editions/" + createdEditionId)
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @Order(23)
    void admin_delete_in_post_sale_phase_returns_422() throws Exception {
        mockMvc.perform(delete("/api/admin/editions/" + createdEditionId)
                        .session(adminSession).with(csrf()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @Order(24)
    void admin_update_commission_rate_frozen_in_closed_phase_returns_422() throws Exception {
        Edition edition = repository.findById(createdEditionId).orElseThrow();
        edition.setPhase(PhaseType.CLOSED);
        repository.save(edition);

        EditionDto dto = new EditionDto(null, "Bourse Datée", null, new BigDecimal("30.00"), null, null, false, null, null);
        mockMvc.perform(put("/api/admin/editions/" + createdEditionId)
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @Order(25)
    void admin_delete_in_closed_phase_returns_422() throws Exception {
        mockMvc.perform(delete("/api/admin/editions/" + createdEditionId)
                        .session(adminSession).with(csrf()))
                .andExpect(status().isUnprocessableEntity());
    }

    // Story 2.1 tested 403-on-GET for a volunteer session (Order 2), but mutations were untested.
    // POST/PATCH/DELETE are all protected by the same @PreAuthorize; the temp edition created in
    // setUpSessions() was PREPARATION/deleted already, so these calls target a fresh, disposable edition.

    @Test
    @Order(26)
    void volunteer_post_returns_403() throws Exception {
        EditionDto dto = new EditionDto(null, "Volunteer Attempt", null, null, null, null, false, null, null);
        mockMvc.perform(post("/api/admin/editions")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(27)
    void volunteer_put_returns_403() throws Exception {
        EditionDto dto = new EditionDto(null, "Volunteer Attempt", null, null, null, null, false, null, null);
        mockMvc.perform(put("/api/admin/editions/" + createdEditionId)
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(28)
    void volunteer_delete_returns_403() throws Exception {
        mockMvc.perform(delete("/api/admin/editions/" + createdEditionId)
                        .session(volunteerSession).with(csrf()))
                .andExpect(status().isForbidden());
    }
}
