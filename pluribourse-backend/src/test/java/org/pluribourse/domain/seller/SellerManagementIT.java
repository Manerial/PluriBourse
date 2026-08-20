package org.pluribourse.domain.seller;

import com.fasterxml.jackson.core.type.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.pluribourse.domain.edition.dto.*;
import org.pluribourse.domain.seller.dto.*;
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
class SellerManagementIT extends IntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private MockHttpSession adminSession;
    private MockHttpSession volunteerSession;
    private MockHttpSession sellerSession;
    private Long editionId;
    private Long sellerId;

    @BeforeAll
    void setUpSessions() throws Exception {
        MvcResult adminLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "test_admin")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andReturn();
        adminSession = (MockHttpSession) adminLogin.getRequest().getSession(false);

        MvcResult editionResult = mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditionDto(null, "Bourse Vendeurs 2026", null, null, null, null, false, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3), null))))
                .andExpect(status().isCreated())
                .andReturn();
        editionId = objectMapper.readValue(editionResult.getResponse().getContentAsString(), EditionDto.class).id();

        MvcResult volunteerLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "volunteer1")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andReturn();
        volunteerSession = (MockHttpSession) volunteerLogin.getRequest().getSession(false);

        MvcResult sellerLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "seller1")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andReturn();
        sellerSession = (MockHttpSession) sellerLogin.getRequest().getSession(false);
    }

    @Test
    @Order(1)
    void search_during_preparation_phase_is_blocked() throws Exception {
        mockMvc.perform(get("/api/sellers/search").param("query", "Martin").session(volunteerSession))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/seller-management-locked")));
    }

    @Test
    @Order(2)
    void create_during_preparation_phase_is_blocked() throws Exception {
        SellerDto payload = new SellerDto(null, "Pierre", "Martin", "martin.pierre@email.com", "0612345678");
        mockMvc.perform(post("/api/sellers")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/seller-management-locked")));
    }

    @Test
    @Order(3)
    void advance_edition_to_deposit_phase() throws Exception {
        mockMvc.perform(put("/api/admin/editions/" + editionId + "/categories")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(
                                Map.of("name", "Jouets", "tableNumbers", List.of(1))))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("DEPOSIT"));
    }

    @Test
    @Order(4)
    void search_with_no_sellers_returns_empty_list() throws Exception {
        mockMvc.perform(get("/api/sellers/search").param("query", "Martin").session(volunteerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @Order(5)
    void volunteer_creates_seller_profile() throws Exception {
        SellerDto payload = new SellerDto(null, "Pierre", "Martin", "martin.pierre@email.com", "0612345678");
        MvcResult result = mockMvc.perform(post("/api/sellers")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();
        SellerDto created = objectMapper.readValue(result.getResponse().getContentAsString(), SellerDto.class);
        sellerId = created.id();
        assertThat(sellerId).isNotNull();
        assertThat(created.firstName()).isEqualTo("Pierre");
        assertThat(created.email()).isEqualTo("martin.pierre@email.com");
    }

    @Test
    @Order(6)
    void search_finds_created_seller_case_insensitively() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/sellers/search").param("query", "martin").session(volunteerSession))
                .andExpect(status().isOk())
                .andReturn();
        List<SellerDto> found = objectMapper.readValue(
                result.getResponse().getContentAsString(), new TypeReference<>() {
                });
        assertThat(found).hasSize(1);
        assertThat(found.getFirst().id()).isEqualTo(sellerId);
    }

    @Test
    @Order(7)
    void search_finds_created_seller_by_email() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/sellers/search").param("query", "email.com").session(volunteerSession))
                .andExpect(status().isOk())
                .andReturn();
        List<SellerDto> found = objectMapper.readValue(
                result.getResponse().getContentAsString(), new TypeReference<>() {
                });
        assertThat(found).hasSize(1);
    }

    @Test
    @Order(8)
    void search_treats_percent_and_underscore_as_literal_characters_not_sql_wildcards() throws Exception {
        // Without escaping, "%" alone would LIKE-match every seller in the edition (enumeration risk).
        mockMvc.perform(get("/api/sellers/search").param("query", "%").session(volunteerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/sellers/search").param("query", "_artin").session(volunteerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @Order(9)
    void create_with_email_already_used_in_active_edition_returns_422() throws Exception {
        SellerDto payload = new SellerDto(null, "Pierrot", "Martini", "martin.pierre@email.com", "0698765432");
        mockMvc.perform(post("/api/sellers")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/seller-email-already-exists")));
    }

    @Test
    @Order(10)
    void create_with_invalid_email_returns_400_rfc7807() throws Exception {
        // AC5: epic says 422, but GlobalExceptionHandler.handleMethodArgumentNotValid returns 400
        // for @Valid DTO violations — same precedent as EditionManagementIT#create_edition_with_blank_name_returns_400_rfc7807
        SellerDto payload = new SellerDto(null, "Jean", "Dupont", "not-an-email", "0612345678");
        mockMvc.perform(post("/api/sellers")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://pluribourse/errors/validation-failed"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @Order(11)
    void create_with_missing_required_field_returns_400_rfc7807() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "firstName", "Jean",
                "lastName", "Dupont",
                "email", "jean.dupont@email.com"
        ));
        mockMvc.perform(post("/api/sellers")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://pluribourse/errors/validation-failed"));
    }

    @Test
    @Order(12)
    void admin_lists_sellers_paginated() throws Exception {
        mockMvc.perform(get("/api/admin/sellers").param("size", "50").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @Order(13)
    void volunteer_cannot_list_admin_sellers() throws Exception {
        mockMvc.perform(get("/api/admin/sellers").session(volunteerSession))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(14)
    void seller_role_cannot_search_sellers() throws Exception {
        mockMvc.perform(get("/api/sellers/search").param("query", "Martin").session(sellerSession))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(15)
    void seller_role_cannot_create_seller() throws Exception {
        SellerDto payload = new SellerDto(null, "Nouveau", "Vendeur", "nouveau.vendeur@email.com", "0611223344");
        mockMvc.perform(post("/api/sellers")
                        .session(sellerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(16)
    void advance_edition_to_sale_phase() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("SALE"));
    }

    @Test
    @Order(17)
    void admin_delete_refused_outside_deposit_phase() throws Exception {
        mockMvc.perform(delete("/api/admin/sellers/" + sellerId)
                        .session(adminSession).with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/seller-deletion-locked")));
    }

    @Test
    @Order(18)
    void rollback_edition_to_deposit_phase() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/rollback")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("DEPOSIT"));
    }

    @Test
    @Order(19)
    void admin_deletes_seller_in_deposit_phase() throws Exception {
        mockMvc.perform(delete("/api/admin/sellers/" + sellerId)
                        .session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(20)
    void search_no_longer_finds_deleted_seller() throws Exception {
        mockMvc.perform(get("/api/sellers/search").param("query", "martin").session(volunteerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @Order(21)
    void delete_unknown_seller_returns_404() throws Exception {
        mockMvc.perform(delete("/api/admin/sellers/999999")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/seller-not-found")));
    }
}
