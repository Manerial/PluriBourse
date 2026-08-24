package org.pluribourse.domain.payout;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.pluribourse.domain.edition.dto.EditionCategoryDto;
import org.pluribourse.domain.edition.dto.EditionDto;
import org.pluribourse.domain.item.dto.CreateItemDto;
import org.pluribourse.domain.payout.dto.SettleDto;
import org.pluribourse.domain.payout.dto.SettlementDto;
import org.pluribourse.domain.pos.dto.ValidateBasketDto;
import org.pluribourse.domain.pos.entity.PaymentMethod;
import org.pluribourse.domain.report.dto.EditionSummaryReportDto;
import org.pluribourse.domain.seller.dto.SellerDto;
import org.pluribourse.shared.IntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 5.1 — seller settlement flow (FR-050 to FR-053, FR-095). Alice (seller 1) sells one item
 * (5.00€, 20% default commission → 4.00€ net due) via the existing POS flow to get a real
 * {@code sold = true} item; Bob (seller 2) sells nothing, so his amount due stays 0.00€ — proving
 * an implicit UNSETTLED status with no {@code Settlement} row is computed correctly on both a
 * seller with and without a due amount.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SettlementIT extends IntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private static final String ITEM_1_BARCODE = "00010001";

    private MockHttpSession adminSession;
    private MockHttpSession volunteer1Session;
    private Long editionId;
    private Long aliceId;
    private Long bobId;

    @BeforeAll
    void setUpSessions() throws Exception {
        MvcResult adminLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "test_admin")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andReturn();
        adminSession = (MockHttpSession) adminLogin.getRequest().getSession(false);

        MvcResult volunteer1Login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "volunteer1")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andReturn();
        volunteer1Session = (MockHttpSession) volunteer1Login.getRequest().getSession(false);
    }

    @Test
    @Order(1)
    void create_edition_with_sellers_and_sell_alices_item() throws Exception {
        MvcResult editionResult = mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditionDto(null, "Bourse Reversements 2026", null, null, null, null, false, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3), null))))
                .andExpect(status().isCreated())
                .andReturn();
        editionId = objectMapper.readValue(editionResult.getResponse().getContentAsString(), EditionDto.class).id();

        List<EditionCategoryDto> categoriesPayload = List.of(new EditionCategoryDto(null, "Jouets", List.of(1)));
        MvcResult categoriesResult = mockMvc.perform(put("/api/admin/editions/" + editionId + "/categories")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(categoriesPayload)))
                .andExpect(status().isOk())
                .andReturn();
        Long categoryId = objectMapper.readValue(
                categoriesResult.getResponse().getContentAsString(), new TypeReference<List<EditionCategoryDto>>() {
                }).get(0).id();

        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("DEPOSIT"));

        MvcResult aliceResult = mockMvc.perform(post("/api/sellers")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SellerDto(null, "Alice", "Vendeuse", "alice.solde@email.com", "0600000001"))))
                .andExpect(status().isCreated())
                .andReturn();
        aliceId = objectMapper.readValue(aliceResult.getResponse().getContentAsString(), SellerDto.class).id();

        MvcResult bobResult = mockMvc.perform(post("/api/sellers")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SellerDto(null, "Bob", "Vendeur", "bob.solde@email.com", "0600000002"))))
                .andExpect(status().isCreated())
                .andReturn();
        bobId = objectMapper.readValue(bobResult.getResponse().getContentAsString(), SellerDto.class).id();

        CreateItemDto itemPayload = new CreateItemDto(aliceId, categoryId, "Kapla", new BigDecimal("5.00"), false, null);
        mockMvc.perform(post("/api/items")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(itemPayload)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("SALE"));

        MvcResult basketResult = mockMvc.perform(get("/api/pos/baskets/current").session(volunteer1Session))
                .andExpect(status().isOk())
                .andReturn();
        Long basketId = objectMapper.readTree(basketResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/pos/baskets/" + basketId + "/items")
                        .session(volunteer1Session).with(csrf())
                        .param("barcode", ITEM_1_BARCODE))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/pos/baskets/" + basketId + "/validate")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ValidateBasketDto(PaymentMethod.CASH, new BigDecimal("5.00")))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("POST_SALE"));
    }

    @Test
    @Order(2)
    void settlements_list_shows_amount_due_and_unsettled_status_before_any_settlement() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/settlements").session(volunteer1Session))
                .andExpect(status().isOk())
                .andReturn();
        List<SettlementDto> settlements = objectMapper.readValue(
                result.getResponse().getContentAsString(), new TypeReference<List<SettlementDto>>() {
                });

        assertThat(settlements).hasSize(2);
        SettlementDto alice = settlementOf(settlements, aliceId);
        assertThat(alice.amountDue()).isEqualByComparingTo("4.00");
        assertThat(alice.amountPaid()).isNull();
        assertThat(alice.status().name()).isEqualTo("UNSETTLED");

        SettlementDto bob = settlementOf(settlements, bobId);
        assertThat(bob.amountDue()).isEqualByComparingTo("0.00");
        assertThat(bob.amountPaid()).isNull();
        assertThat(bob.status().name()).isEqualTo("UNSETTLED");
    }

    @Test
    @Order(3)
    void settling_below_due_amount_is_accepted_as_a_warning_not_a_block() throws Exception {
        mockMvc.perform(post("/api/settlements/" + aliceId + "/settle")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SettleDto(new BigDecimal("3.00")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"));

        MvcResult result = mockMvc.perform(get("/api/settlements").session(volunteer1Session))
                .andExpect(status().isOk())
                .andReturn();
        List<SettlementDto> settlements = objectMapper.readValue(
                result.getResponse().getContentAsString(), new TypeReference<List<SettlementDto>>() {
                });
        SettlementDto alice = settlementOf(settlements, aliceId);
        assertThat(alice.status().name()).isEqualTo("SETTLED");
        // amountPaid mirrors what the volunteer actually entered (3.00), not the 4.00 due.
        assertThat(alice.amountPaid()).isEqualByComparingTo("3.00");
    }

    @Test
    @Order(4)
    void settling_above_due_amount_is_blocked() throws Exception {
        mockMvc.perform(post("/api/settlements/" + bobId + "/settle")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SettleDto(new BigDecimal("0.01")))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(endsWith("/invalid-settlement-amount")));
    }

    @Test
    @Order(5)
    void settling_an_already_settled_seller_is_rejected() throws Exception {
        mockMvc.perform(post("/api/settlements/" + aliceId + "/settle")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SettleDto(new BigDecimal("1.00")))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(endsWith("/seller-already-settled")));
    }

    @Test
    @Order(6)
    void marking_a_seller_unclaimed_records_the_full_amount_due() throws Exception {
        mockMvc.perform(post("/api/settlements/" + bobId + "/unclaimed")
                        .session(volunteer1Session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNCLAIMED"))
                .andExpect(jsonPath("$.amountDue").value(0.00))
                // Nothing physically handed to a "Non réclamé" seller — amountPaid stays null even
                // though the amount is fully due (transferred to the association instead).
                .andExpect(jsonPath("$.amountPaid").value(nullValue()));
    }

    @Test
    @Order(7)
    void settlement_endpoint_is_rejected_outside_the_post_sale_phase() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/rollback")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("SALE"));

        mockMvc.perform(get("/api/settlements").session(volunteer1Session))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(endsWith("/settlement-not-allowed")));

        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("POST_SALE"));
    }

    @Test
    @Order(8)
    void settlement_endpoint_is_reachable_by_both_admin_and_volunteer_sessions() throws Exception {
        mockMvc.perform(get("/api/settlements").session(adminSession))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/settlements").session(volunteer1Session))
                .andExpect(status().isOk());
    }

    /**
     * IDOR proof (requireSellerOfEdition): closes the edition 1 (only one edition can be active
     * at a time — {@code EditionService.createEdition} rejects a second one otherwise) so a real
     * second edition can become active, then settles Alice — who still belongs to edition 1,
     * now CLOSED — against it. The generic 404 (not a 422/403) is the actual proof: it must be
     * indistinguishable from a seller that doesn't exist at all.
     */
    @Test
    @Order(9)
    void settle_rejects_a_seller_from_a_different_edition_with_a_generic_not_found() throws Exception {
        // POST_SALE → CLOSED only via the dedicated /close endpoint (FR-096 follow-up fix).
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/close")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("CLOSED"));

        MvcResult edition2Result = mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditionDto(null, "Bourse Reversements 2027", null, null, null, null, false, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 3), null))))
                .andExpect(status().isCreated())
                .andReturn();
        Long edition2Id = objectMapper.readValue(edition2Result.getResponse().getContentAsString(), EditionDto.class).id();

        List<EditionCategoryDto> categoriesPayload = List.of(new EditionCategoryDto(null, "Jouets", List.of(1)));
        mockMvc.perform(put("/api/admin/editions/" + edition2Id + "/categories")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(categoriesPayload)))
                .andExpect(status().isOk());

        for (String expectedPhase : List.of("DEPOSIT", "SALE", "POST_SALE")) {
            mockMvc.perform(post("/api/admin/editions/" + edition2Id + "/phase/advance")
                            .session(adminSession).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.phase").value(expectedPhase));
        }

        mockMvc.perform(post("/api/settlements/" + aliceId + "/settle")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SettleDto(new BigDecimal("1.00")))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(endsWith("/seller-not-found")));
    }

    @Test
    @Order(10)
    void settle_rejects_an_invalid_amount_before_reaching_business_logic() throws Exception {
        mockMvc.perform(post("/api/settlements/" + aliceId + "/settle")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": -1.00}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/settlements/" + aliceId + "/settle")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Edition report's "total des reversements nets" must reflect what was actually handed to
     * sellers, not the theoretical grossRevenue - commission (5.00 - 1.00 = 4.00 here): Alice was
     * settled below her due amount (3.00 of 4.00, Order 3) and Bob was marked Non réclamé (Order
     * 6, nothing physically paid to him, his due amount goes to the association instead).
     */
    @Test
    @Order(11)
    void edition_report_net_payout_total_reflects_actually_paid_amounts_not_amounts_due() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/admin/reports/edition/" + editionId).session(adminSession))
                .andExpect(status().isOk())
                .andReturn();
        EditionSummaryReportDto report = objectMapper.readValue(result.getResponse().getContentAsString(), EditionSummaryReportDto.class);

        assertThat(report.netPayoutTotal()).isEqualByComparingTo("3.00");
    }

    private SettlementDto settlementOf(List<SettlementDto> settlements, Long sellerId) {
        return settlements.stream().filter(s -> s.sellerId().equals(sellerId)).findFirst().orElseThrow();
    }
}
