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
import org.pluribourse.domain.pos.dto.ValidateBasketDto;
import org.pluribourse.domain.pos.entity.PaymentMethod;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 5.7 — the {@code settlement-updated} SSE event that keeps the settlement screens of the
 * other terminals in sync (ARCH-017). Alice (seller 1) sells one 5.00€ item (20% default
 * commission → 4.00€ net due) via the real POS flow; Bob (seller 2) sells nothing, so his amount
 * due stays 0.00€ — used to prove that a rejected settle broadcasts nothing.
 * <p>
 * A fresh SSE connection is opened for every scenario ({@code SseEmitterRegistry} replays no
 * history) so a "no settlement-updated" assertion is never masked by an event from an earlier
 * connection — same pattern as {@code PosBasketCancellationIT}. Kept as a dedicated storyboard
 * rather than extra {@code @Order} methods on {@code SettlementIT}, whose @Order(9) closes
 * edition 1 and switches to a second edition (stories 5.5/5.6 pattern).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SettlementSyncIT extends IntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private static final String ALICE_ITEM_BARCODE = "00010001";

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
                        .content(objectMapper.writeValueAsString(new EditionDto(null, "Bourse Synchro Soldage 2026", null, null, null, null, false, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3), null, null))))
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
                        .content(objectMapper.writeValueAsString(new SellerDto(null, "Alice", "Vendeuse", "alice.sync@email.com", "0600000001"))))
                .andExpect(status().isCreated())
                .andReturn();
        aliceId = objectMapper.readValue(aliceResult.getResponse().getContentAsString(), SellerDto.class).id();

        MvcResult bobResult = mockMvc.perform(post("/api/sellers")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SellerDto(null, "Bob", "Vendeur", "bob.sync@email.com", "0600000002"))))
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
                        .param("barcode", ALICE_ITEM_BARCODE))
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
    void a_rejected_settle_broadcasts_no_settlement_updated() throws Exception {
        MvcResult sse = mockMvc.perform(get("/api/sse/events").session(volunteer1Session))
                .andExpect(request().asyncStarted())
                .andReturn();

        // Bob is owed 0.00 — settling 0.01 is strictly above the due amount → 422, no persistence.
        mockMvc.perform(post("/api/settlements/" + bobId + "/settle")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SettleDto(new BigDecimal("0.01")))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(endsWith("/invalid-settlement-amount")));

        assertThat(sse.getResponse().getContentAsString()).doesNotContain("settlement-updated");
    }

    @Test
    @Order(3)
    void settling_alice_broadcasts_settlement_updated_with_edition_and_seller_ids() throws Exception {
        MvcResult sse = mockMvc.perform(get("/api/sse/events").session(volunteer1Session))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(post("/api/settlements/" + aliceId + "/settle")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SettleDto(new BigDecimal("3.00")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"));

        String sseBody = sse.getResponse().getContentAsString();
        assertThat(sseBody).contains("settlement-updated");
        assertThat(sseBody).contains("\"editionId\":" + editionId);
        assertThat(sseBody).contains("\"sellerId\":" + aliceId);
    }

    @Test
    @Order(4)
    void marking_bob_unclaimed_broadcasts_settlement_updated() throws Exception {
        MvcResult sse = mockMvc.perform(get("/api/sse/events").session(volunteer1Session))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(post("/api/settlements/" + bobId + "/unclaimed")
                        .session(volunteer1Session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNCLAIMED"));

        String sseBody = sse.getResponse().getContentAsString();
        assertThat(sseBody).contains("settlement-updated");
        assertThat(sseBody).contains("\"editionId\":" + editionId);
        assertThat(sseBody).contains("\"sellerId\":" + bobId);
    }
}
