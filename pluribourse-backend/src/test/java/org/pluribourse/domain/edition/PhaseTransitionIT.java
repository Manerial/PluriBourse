package org.pluribourse.domain.edition;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PhaseTransitionIT extends IntegrationTest {

    @Autowired
    private EditionRepository repository;
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

        // Volunteer login requires an active edition (Story 2.3 gate); create one temporarily.
        MvcResult tempEdition = mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Setup Edition\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-03\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long tempEditionId = objectMapper.readTree(tempEdition.getResponse().getContentAsString()).get("id").asLong();

        MvcResult volunteerLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "volunteer1")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andReturn();
        volunteerSession = (MockHttpSession) volunteerLogin.getRequest().getSession(false);

        // Remove the temporary edition so @Order(1) can create its own without the "already active" conflict.
        // The volunteer session remains valid (Story 2.3 AC5 — session invalidation is out of scope).
        mockMvc.perform(delete("/api/admin/editions/" + tempEditionId)
                        .session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(1)
    void create_edition_in_preparation() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditionDto(null, "Bourse Test 2026", null, null, null, null, false, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3), null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phase").value("PREPARATION"))
                .andExpect(jsonPath("$.archived").value(false))
                .andReturn();
        editionId = objectMapper.readValue(result.getResponse().getContentAsString(), EditionDto.class).id();
        assertThat(editionId).isNotNull();
    }

    @Test
    @Order(2)
    void get_by_id_returns_edition() throws Exception {
        mockMvc.perform(get("/api/admin/editions/" + editionId).session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(editionId))
                .andExpect(jsonPath("$.phase").value("PREPARATION"));
    }

    @Test
    @Order(3)
    void rollback_from_preparation_returns_422() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/rollback")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    @Order(4)
    void advance_to_deposit_without_categories_returns_422() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/no-categories-configured")));

        Edition edition = repository.findById(editionId).orElseThrow();
        assertThat(edition.getPhase()).isEqualTo(PhaseType.PREPARATION);
    }

    @Test
    @Order(5)
    void advance_to_deposit_locks_commission_rate() throws Exception {
        mockMvc.perform(put("/api/admin/editions/" + editionId + "/categories")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(new EditionCategoryDto(null, "Jouets", List.of(1, 2))))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("DEPOSIT"));

        Edition edition = repository.findById(editionId).orElseThrow();
        assertThat(edition.getPhase()).isEqualTo(PhaseType.DEPOSIT);
    }

    @Test
    @Order(6)
    void advance_to_deposit_blocked_when_another_edition_already_active() throws Exception {
        // FR-105 (Story 2.10) : editionId est déjà en DEPOSIT (Order 5) — une deuxième édition qui
        // tente de l'atteindre pendant ce temps doit être refusée, même si rien ne bloquait sa
        // création ni sa configuration en Préparation (FR-010 amendé — plusieurs éditions peuvent
        // coexister en Préparation).
        MvcResult result = mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditionDto(null, "Bourse Suivante", null, null, null, null, false, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3), null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phase").value("PREPARATION"))
                .andReturn();
        Long secondEditionId = objectMapper.readValue(result.getResponse().getContentAsString(), EditionDto.class).id();

        mockMvc.perform(put("/api/admin/editions/" + secondEditionId + "/categories")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(new EditionCategoryDto(null, "Livres", List.of(3))))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/editions/" + secondEditionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/edition-already-active")));

        Edition edition = repository.findById(secondEditionId).orElseThrow();
        assertThat(edition.getPhase()).isEqualTo(PhaseType.PREPARATION);

        // Nettoyage — toujours en PREPARATION, suppression autorisée, ne doit pas persister
        // jusqu'aux étapes suivantes du storyboard.
        mockMvc.perform(delete("/api/admin/editions/" + secondEditionId)
                        .session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(7)
    void commission_rate_update_rejected_in_deposit() throws Exception {
        String body = "{\"name\":\"Bourse Test 2026\",\"commissionRate\":10.00,\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-03\"}";
        mockMvc.perform(put("/api/admin/editions/" + editionId)
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    @Order(8)
    void rollback_deposit_to_preparation_unlocks_commission() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/rollback")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("PREPARATION"));

        String body = "{\"name\":\"Bourse Test 2026\",\"commissionRate\":18.00,\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-03\"}";
        mockMvc.perform(put("/api/admin/editions/" + editionId)
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commissionRate").value(18.00));
    }

    @Test
    @Order(9)
    void advance_through_all_phases_to_closed() throws Exception {
        // PREPARATION → DEPOSIT
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("DEPOSIT"));

        // DEPOSIT → SALE
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("SALE"));

        // SALE → POST_SALE
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("POST_SALE"));

        // POST_SALE → CLOSED is refused by the generic endpoint on purpose (FR-096 follow-up fix)
        // — only the dedicated /close endpoint may perform this exact transition.
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/closing-requires-dedicated-endpoint")));

        mockMvc.perform(post("/api/admin/editions/" + editionId + "/close")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("CLOSED"));
    }

    @Test
    @Order(10)
    void advance_to_deposit_unblocked_once_other_edition_closed() throws Exception {
        // FR-105 : editionId est Clôturée depuis Order 9 — une troisième édition peut désormais
        // atteindre Dépôt.
        MvcResult result = mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditionDto(null, "Bourse Après Clôture", null, null, null, null, false, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3), null))))
                .andExpect(status().isCreated())
                .andReturn();
        Long thirdEditionId = objectMapper.readValue(result.getResponse().getContentAsString(), EditionDto.class).id();

        mockMvc.perform(put("/api/admin/editions/" + thirdEditionId + "/categories")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(new EditionCategoryDto(null, "Vêtements", List.of(4))))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/editions/" + thirdEditionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("DEPOSIT"));

        // Nettoyage impératif : les Order 12/13 suivants font repasser editionId (#1, CLOSED) par
        // POST_SALE via rollback. Ce contrôle (FR-105) ne porte QUE sur la transition
        // Préparation → Dépôt (voir Dev Notes — le rollback CLOSED → POST_SALE n'est pas gardé par
        // cette story) : si cette troisième édition restait en DEPOSIT, le storyboard se
        // retrouverait dans un état à deux éditions actives simultanément, jamais voulu ni
        // spécifié. On la fait donc redescendre et on la supprime pour que la suite du fichier ne
        // dépende jamais de cet état non spécifié.
        mockMvc.perform(post("/api/admin/editions/" + thirdEditionId + "/phase/rollback")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/admin/editions/" + thirdEditionId)
                        .session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(11)
    void advance_from_closed_returns_422() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    @Order(12)
    void rollback_from_closed_to_post_sale_succeeds_when_not_archived() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/rollback")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("POST_SALE"));
    }

    @Test
    @Order(13)
    void rollback_from_closed_blocked_when_archived() throws Exception {
        // Close again (dedicated endpoint — see Order 8)
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/close")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("CLOSED"));

        // Simulate archiving (Story 2.5 will set this via a real endpoint)
        Edition edition = repository.findById(editionId).orElseThrow();
        edition.setArchived(true);
        repository.save(edition);

        // Rollback from CLOSED should be rejected
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/rollback")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    @Order(14)
    void volunteer_cannot_trigger_phase_transitions() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(volunteerSession).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(15)
    void unauthenticated_request_returns_401() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(16)
    void get_by_id_returns_404_for_nonexistent_edition() throws Exception {
        mockMvc.perform(get("/api/admin/editions/99999").session(adminSession))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(17)
    void sse_endpoint_accessible_by_authenticated_admin() throws Exception {
        mockMvc.perform(get("/api/sse/events").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/event-stream")));
    }

    @Test
    @Order(18)
    void sse_endpoint_accessible_by_volunteer() throws Exception {
        mockMvc.perform(get("/api/sse/events").session(volunteerSession))
                .andExpect(status().isOk());
    }

    @Test
    @Order(19)
    void sse_endpoint_returns_401_for_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/sse/events"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(20)
    void multiple_phase_changes_are_all_delivered_over_the_same_sse_connection() throws Exception {
        MvcResult creation = mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bourse SSE Test\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-03\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        Long sseEditionId = objectMapper.readTree(creation.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(put("/api/admin/editions/" + sseEditionId + "/categories")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(new EditionCategoryDto(null, "Jouets", List.of(1, 2))))))
                .andExpect(status().isOk());

        MvcResult sseResult = mockMvc.perform(get("/api/sse/events").session(adminSession))
                .andExpect(request().asyncStarted())
                .andReturn();

        // Two phase advances fired back-to-back must both reach the SAME long-lived connection —
        // if the emitter were closed after the first event, the second would be broadcast to no one.
        mockMvc.perform(post("/api/admin/editions/" + sseEditionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("DEPOSIT"));

        mockMvc.perform(post("/api/admin/editions/" + sseEditionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("SALE"));

        String sseBody = sseResult.getResponse().getContentAsString();
        assertThat(sseBody).contains("\"newPhase\":\"DEPOSIT\"");
        assertThat(sseBody).contains("\"newPhase\":\"SALE\"");
    }
}
