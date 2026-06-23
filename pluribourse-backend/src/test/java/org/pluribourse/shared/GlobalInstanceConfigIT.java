package org.pluribourse.shared;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.pluribourse.shared.instanceconfig.dto.*;
import org.pluribourse.shared.instanceconfig.repository.*;
import org.pluribourse.user.enums.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.http.MediaType;   // EXPLICIT — not wildcard (JUnit 5 / MediaType collision)
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.*;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GlobalInstanceConfigIT extends IntegrationTest {

    @Autowired
    private GlobalInstanceConfigRepository repository;

    @Autowired
    private ObjectMapper objectMapper;

    private MockHttpSession adminSession;
    private MockHttpSession volunteerSession;

    @BeforeAll
    void setUpSessions() throws Exception {
        MvcResult adminLogin = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "test_admin")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andReturn();
        adminSession = (MockHttpSession) adminLogin.getRequest().getSession(false);

        MvcResult volunteerLogin = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "volunteer1")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andReturn();
        volunteerSession = (MockHttpSession) volunteerLogin.getRequest().getSession(false);
    }

    @Test @Order(1)
    void unauthenticated_get_returns_401() throws Exception {
        mockMvc.perform(get("/api/admin/instance-config"))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(2)
    void volunteer_get_returns_403() throws Exception {
        mockMvc.perform(get("/api/admin/instance-config").session(volunteerSession))
                .andExpect(status().isForbidden());
    }

    @Test @Order(3)
    void admin_get_returns_defaults() throws Exception {
        mockMvc.perform(get("/api/admin/instance-config").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.associationName").value(""))
                .andExpect(jsonPath("$.defaultCommissionRate").value(20.00))
                .andExpect(jsonPath("$.defaultDocumentLanguage").value("EN"));
    }

    @Test @Order(4)
    void admin_put_updates_association_name_and_persists() throws Exception {
        var dto = new GlobalInstanceConfigDto("Mon Association", new BigDecimal("20.00"), Language.EN);
        mockMvc.perform(put("/api/admin/instance-config")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.associationName").value("Mon Association"));

        var config = repository.findById(1L).orElseThrow();
        assertThat(config.getAssociationName()).isEqualTo("Mon Association");
    }

    @Test @Order(5)
    void admin_put_commission_rate_stored_as_bigdecimal() throws Exception {
        var dto = new GlobalInstanceConfigDto("Mon Association", new BigDecimal("15.00"), Language.EN);
        mockMvc.perform(put("/api/admin/instance-config")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        var config = repository.findById(1L).orElseThrow();
        assertThat(config.getDefaultCommissionRate().compareTo(new BigDecimal("15"))).isZero();
        assertThat(config.getDefaultCommissionRate().scale()).isEqualTo(2);
    }

    @Test @Order(6)
    void admin_put_document_language_fr_persists() throws Exception {
        var dto = new GlobalInstanceConfigDto("Mon Association", new BigDecimal("15.00"), Language.FR);
        mockMvc.perform(put("/api/admin/instance-config")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultDocumentLanguage").value("FR"));

        var config = repository.findById(1L).orElseThrow();
        assertThat(config.getDefaultDocumentLanguage().name()).isEqualTo("FR");
    }

    @Test @Order(7)
    void admin_put_commission_over_100_returns_400() throws Exception {
        String body = "{\"associationName\":\"Mon Association\",\"defaultCommissionRate\":101,\"defaultDocumentLanguage\":\"EN\"}";
        mockMvc.perform(put("/api/admin/instance-config")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(8)
    void admin_put_invalid_language_returns_400() throws Exception {
        String body = "{\"associationName\":\"Mon Association\",\"defaultCommissionRate\":20,\"defaultDocumentLanguage\":\"DE\"}";
        mockMvc.perform(put("/api/admin/instance-config")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(9)
    void volunteer_put_returns_403() throws Exception {
        String body = "{\"associationName\":\"test\",\"defaultCommissionRate\":20,\"defaultDocumentLanguage\":\"EN\"}";
        mockMvc.perform(put("/api/admin/instance-config")
                        .session(volunteerSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test @Order(10)
    void unauthenticated_put_returns_401() throws Exception {
        String body = "{\"associationName\":\"test\",\"defaultCommissionRate\":20,\"defaultDocumentLanguage\":\"EN\"}";
        mockMvc.perform(put("/api/admin/instance-config")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(11)
    void admin_put_negative_commission_returns_400() throws Exception {
        String body = "{\"associationName\":\"Mon Association\",\"defaultCommissionRate\":-1,\"defaultDocumentLanguage\":\"EN\"}";
        mockMvc.perform(put("/api/admin/instance-config")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(12)
    void admin_put_null_association_name_returns_400() throws Exception {
        String body = "{\"associationName\":null,\"defaultCommissionRate\":20,\"defaultDocumentLanguage\":\"EN\"}";
        mockMvc.perform(put("/api/admin/instance-config")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(13)
    void admin_put_blank_association_name_returns_400() throws Exception {
        String body = "{\"associationName\":\"   \",\"defaultCommissionRate\":20,\"defaultDocumentLanguage\":\"EN\"}";
        mockMvc.perform(put("/api/admin/instance-config")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(14)
    void admin_put_association_name_too_long_returns_400() throws Exception {
        var dto = new GlobalInstanceConfigDto("A".repeat(256), new BigDecimal("20.00"), Language.EN);
        mockMvc.perform(put("/api/admin/instance-config")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(15)
    void admin_put_commission_rate_100_accepted() throws Exception {
        var dto = new GlobalInstanceConfigDto("Mon Association", new BigDecimal("100.00"), Language.EN);
        mockMvc.perform(put("/api/admin/instance-config")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test @Order(16)
    void admin_put_commission_rate_0_accepted() throws Exception {
        var dto = new GlobalInstanceConfigDto("Mon Association", new BigDecimal("0.00"), Language.EN);
        mockMvc.perform(put("/api/admin/instance-config")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test @Order(17)
    void admin_put_commission_rate_excess_decimal_places_returns_400() throws Exception {
        // @Digits(fraction=2) rejects more than 2 decimal places
        String body = "{\"associationName\":\"Mon Association\",\"defaultCommissionRate\":15.123,\"defaultDocumentLanguage\":\"EN\"}";
        mockMvc.perform(put("/api/admin/instance-config")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(18)
    void admin_put_lowercase_language_returns_400() throws Exception {
        // Jackson enum deserialization is case-sensitive — "en" does not match Language.EN
        String body = "{\"associationName\":\"Mon Association\",\"defaultCommissionRate\":20,\"defaultDocumentLanguage\":\"en\"}";
        mockMvc.perform(put("/api/admin/instance-config")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
