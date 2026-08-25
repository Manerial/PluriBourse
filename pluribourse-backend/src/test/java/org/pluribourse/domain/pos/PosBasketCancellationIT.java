package org.pluribourse.domain.pos;

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
import org.pluribourse.domain.pos.repository.BasketItemRepository;
import org.pluribourse.domain.pos.repository.BasketRepository;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 2.8 — server-side basket cancellation on phase transition (FR-090). A seller/three-item
 * inventory is created once, up front, since item creation is only possible in the Deposit phase
 * and this storyboard re-enters the Sale phase twice (via rollback) to prove both transition
 * directions, plus a two-basket scenario (AC 5) that needs a third item. Each transition under
 * test opens its own fresh SSE connection ({@code SseEmitterRegistry} does not replay history) so
 * that "no basket-cancelled" assertions are never masked by an event broadcast on an earlier
 * connection.
 * <p>
 * AC 4 (atomicity on a rejected transition) is proven at the end of the storyboard using a
 * rollback-from-PREPARATION rejection, not a Sale-phase rejection: a {@code Basket} can only exist
 * during the Sale phase ({@code PhaseGuard.requireSalePhase}), and none of the state machine's
 * rejection guards ({@code PhaseAlreadyClosedException}, {@code NoCategoriesConfiguredException},
 * {@code PhaseRollbackFromPreparationException}) ever fire from the Sale phase — so a basket can
 * never coexist with a rejected transition attempt. The honest proof of AC 4 is therefore that a
 * rejected transition broadcasts no SSE event at all (neither {@code phase-changed} nor
 * {@code basket-cancelled}), showing {@code savePhaseThenSendEvent} — and the basket cancellation
 * inside it — is never reached when a guard rejects the request first.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PosBasketCancellationIT extends IntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BasketRepository basketRepository;

    @Autowired
    private BasketItemRepository basketItemRepository;

    private static final String ITEM_1_BARCODE = "00010001";
    private static final String ITEM_2_BARCODE = "00010002";
    private static final String ITEM_3_BARCODE = "00010003";

    private MockHttpSession adminSession;
    private MockHttpSession volunteer1Session;
    private MockHttpSession volunteer2Session;
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

        MvcResult volunteer1Login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "volunteer1")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andReturn();
        volunteer1Session = (MockHttpSession) volunteer1Login.getRequest().getSession(false);

        MvcResult volunteer2Login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "volunteer2")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andReturn();
        volunteer2Session = (MockHttpSession) volunteer2Login.getRequest().getSession(false);
    }

    @Test
    @Order(1)
    void create_edition_with_three_items_and_advance_to_sale() throws Exception {
        MvcResult editionResult = mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditionDto(null, "Bourse Annulation Panier 2026", null, null, null, null, false, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3), null, null))))
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

        MvcResult sellerResult = mockMvc.perform(post("/api/sellers")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SellerDto(null, "Alice", "Vendeuse", "alice.annulation@email.com", "0600000001"))))
                .andExpect(status().isCreated())
                .andReturn();
        Long sellerId = objectMapper.readValue(sellerResult.getResponse().getContentAsString(), SellerDto.class).id();

        createItem(sellerId, categoryId, "Kapla", "5.00");
        createItem(sellerId, categoryId, "Puzzle", "3.00");
        createItem(sellerId, categoryId, "Extra", "2.00");

        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("SALE"));
    }

    private void createItem(Long sellerId, Long categoryId, String name, String price) throws Exception {
        CreateItemDto payload = new CreateItemDto(sellerId, categoryId, name, new BigDecimal(price), false, null);
        mockMvc.perform(post("/api/items")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated());
    }

    /**
     * Also covers AC 5 (scope: every basket of the edition, not just one bénévole's) — two
     * volunteers each hold an active basket before the transition, and both are proven gone
     * afterward via a single {@code basketRepository} query keyed only on {@code editionId}. Also
     * covers the "et leurs BasketItem" half of AC 1 directly (not by inference from the DB cascade
     * FK): {@code basketItemRepository} is queried per basket after the transition.
     */
    @Test
    @Order(2)
    void advance_with_two_active_baskets_cancels_both_and_broadcasts_once() throws Exception {
        Long basket1Id = createBasketWithItem(volunteer1Session, ITEM_1_BARCODE);
        Long basket2Id = createBasketWithItem(volunteer2Session, ITEM_3_BARCODE);
        assertThat(basketRepository.findAllByEditionId(editionId)).hasSize(2);

        MvcResult sse = mockMvc.perform(get("/api/sse/events").session(adminSession))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("POST_SALE"));

        assertThat(basketRepository.findAllByEditionId(editionId)).isEmpty();
        assertThat(basketItemRepository.findAllByBasketIdOrderById(basket1Id)).isEmpty();
        assertThat(basketItemRepository.findAllByBasketIdOrderById(basket2Id)).isEmpty();

        String sseBody = sse.getResponse().getContentAsString();
        assertThat(sseBody).contains("\"newPhase\":\"POST_SALE\"");
        assertThat(sseBody).contains("basket-cancelled");
        assertThat(sseBody).contains("\"editionId\":" + editionId);
    }

    @Test
    @Order(3)
    void rollback_without_active_basket_does_not_broadcast_basket_cancelled() throws Exception {
        assertThat(basketRepository.findAllByEditionId(editionId)).isEmpty();

        MvcResult sse = mockMvc.perform(get("/api/sse/events").session(adminSession))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/rollback")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("SALE"));

        String sseBody = sse.getResponse().getContentAsString();
        assertThat(sseBody).contains("phase-changed");
        assertThat(sseBody).doesNotContain("basket-cancelled");
    }

    @Test
    @Order(4)
    void rollback_with_active_basket_cancels_it_and_broadcasts() throws Exception {
        createBasketWithItem(volunteer1Session, ITEM_2_BARCODE);

        MvcResult sse = mockMvc.perform(get("/api/sse/events").session(adminSession))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/rollback")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("DEPOSIT"));

        assertThat(basketRepository.findAllByEditionId(editionId)).isEmpty();

        String sseBody = sse.getResponse().getContentAsString();
        assertThat(sseBody).contains("\"newPhase\":\"DEPOSIT\"");
        assertThat(sseBody).contains("basket-cancelled");
    }

    @Test
    @Order(5)
    void rollback_to_preparation_reaches_the_edge_of_the_state_machine() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/rollback")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("PREPARATION"));
    }

    /**
     * AC 4: a rejected transition broadcasts nothing at all. See the class Javadoc for why this
     * scenario — unlike the other three — cannot involve a real {@code Basket}: the state machine
     * makes "active basket" and "rejected transition" mutually exclusive.
     */
    @Test
    @Order(6)
    void rejected_transition_broadcasts_no_event_at_all() throws Exception {
        MvcResult sse = mockMvc.perform(get("/api/sse/events").session(adminSession))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/rollback")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(get("/api/admin/editions/" + editionId).session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("PREPARATION"));

        assertThat(sse.getResponse().getContentAsString()).isEmpty();
    }

    private Long createBasketWithItem(MockHttpSession session, String barcode) throws Exception {
        MvcResult basketResult = mockMvc.perform(get("/api/pos/baskets/current").session(session))
                .andExpect(status().isOk())
                .andReturn();
        Long basketId = objectMapper.readTree(basketResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/pos/baskets/" + basketId + "/items")
                        .session(session).with(csrf())
                        .param("barcode", barcode))
                .andExpect(status().isOk());
        return basketId;
    }
}
