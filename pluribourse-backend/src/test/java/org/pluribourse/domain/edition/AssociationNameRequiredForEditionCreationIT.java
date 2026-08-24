package org.pluribourse.domain.edition;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.pluribourse.domain.edition.dto.EditionDto;
import org.pluribourse.domain.instanceconfig.entity.GlobalInstanceConfig;
import org.pluribourse.domain.instanceconfig.repository.GlobalInstanceConfigRepository;
import org.pluribourse.shared.IntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.hamcrest.Matchers.endsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * An edition cannot be created before the association name is configured (follow-up fix,
 * 2026-08-24): the migration-seeded default ("", migration 004) would otherwise let an
 * un-configured instance produce documents (bordereaux, factures) with no association identity.
 * test-data.sql sets a non-blank name for every other scenario in this suite — this class blanks
 * it back out directly via the repository, since {@code GlobalInstanceConfigDto}'s
 * {@code @NotBlank} makes that state unreachable through the update endpoint itself.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AssociationNameRequiredForEditionCreationIT extends IntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private GlobalInstanceConfigRepository globalInstanceConfigRepository;

    private MockHttpSession adminSession;

    @Test
    @Order(1)
    void admin_login_and_blank_the_association_name() throws Exception {
        MvcResult adminLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "test_admin")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andReturn();
        adminSession = (MockHttpSession) adminLogin.getRequest().getSession(false);

        blankAssociationName();
    }

    @Test
    @Order(2)
    void create_edition_without_an_association_name_returns_422() throws Exception {
        mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditionDto(null, "Bourse Sans Association", null, null, null, null, false, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3), null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(endsWith("/association-name-not-configured")));
    }

    @Test
    @Order(3)
    void create_edition_succeeds_once_the_association_name_is_configured() throws Exception {
        mockMvc.perform(put("/api/admin/instance-config")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"associationName\":\"Les Amis de l'École\",\"defaultCommissionRate\":10.00,\"defaultDocumentLanguage\":\"FR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.associationName").value("Les Amis de l'École"));

        mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditionDto(null, "Bourse Avec Association", null, null, null, null, false, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3), null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phase").value("PREPARATION"));
    }

    private void blankAssociationName() {
        GlobalInstanceConfig config = globalInstanceConfigRepository.findById(1L).orElseThrow();
        config.setAssociationName("");
        globalInstanceConfigRepository.saveAndFlush(config);
    }
}
