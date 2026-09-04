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
import org.pluribourse.domain.item.dto.CreateLotDto;
import org.pluribourse.domain.item.dto.CreateLotItemDto;
import org.pluribourse.domain.item.repository.LotRepository;
import org.pluribourse.domain.pos.dto.BasketDto;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 4.8 Part B (FR-110) — an explicit {@code POST /auth/logout} cancels the volunteer's own
 * active POS basket and releases its lot reservations, and nothing else: another cashier's
 * in-progress basket is untouched (guard-rail C1), and repeated / basket-less logouts still return
 * 200 (best-effort). SSE is not asserted — no client is subscribed under MockMvc, and the logout
 * path emits no broadcast by construction ({@code cancelBasketSilently} registers no synchronization).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PosBasketLogoutCancellationIT extends IntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BasketRepository basketRepository;

    @Autowired
    private BasketItemRepository basketItemRepository;

    @Autowired
    private LotRepository lotRepository;

    private static final String STANDALONE_A_BARCODE = "00010001";
    private static final String LOT_MEMBER_BARCODE = "00010002";
    private static final String STANDALONE_B_BARCODE = "00010004";

    private MockHttpSession adminSession;
    private MockHttpSession volunteer1Session;
    private MockHttpSession volunteer2Session;
    private Long editionId;
    private Long lotId;
    private Long volunteer1BasketId;
    private Long volunteer2BasketId;

    @BeforeAll
    void setUpSessions() throws Exception {
        adminSession = login("test_admin", "Admin");
        volunteer1Session = login("volunteer1", "Admin");
        volunteer2Session = login("volunteer2", "Admin");
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", username)
                        .param("password", password))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    /**
     * Story 4.8 Part B (AC-B2) — a logout while no edition is in an active phase (here: before any
     * edition exists at all): the handler's {@code findFirstByPhaseIn(PhaseType.ACTIVE)} is empty,
     * so it no-ops and the logout still returns 200. Uses a throwaway session so the shared
     * volunteer sessions stay intact for the ordered scenario that follows.
     */
    @Test
    @Order(0)
    void logout_with_no_active_edition_still_returns_200() throws Exception {
        MockHttpSession throwaway = login("volunteer2", "Admin");
        mockMvc.perform(post("/api/auth/logout").session(throwaway).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @Order(1)
    void create_edition_advance_to_sale_with_a_seller_a_lot_and_two_standalone_items() throws Exception {
        MvcResult editionResult = mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditionDto(null, "Bourse Déconnexion 2026", null, null, null, null, false, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3), null, null))))
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
                        .content(objectMapper.writeValueAsString(new SellerDto(null, "Alice", "Vendeuse", "alice.deco@email.com", "0600000001"))))
                .andExpect(status().isCreated())
                .andReturn();
        Long sellerId = objectMapper.readValue(sellerResult.getResponse().getContentAsString(), SellerDto.class).id();

        createItem(sellerId, categoryId, "Article A", "5.00");

        CreateLotDto lotPayload = new CreateLotDto(sellerId, categoryId, "Lot Test", new BigDecimal("6.00"),
                List.of(new CreateLotItemDto("Lot member A", false, null),
                        new CreateLotItemDto("Lot member B", false, null)));
        mockMvc.perform(post("/api/lots")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lotPayload)))
                .andExpect(status().isCreated());

        createItem(sellerId, categoryId, "Article B", "3.00");

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
                .andExpect(status().isCreated())
                .andReturn();
    }

    @Test
    @Order(2)
    void both_volunteers_fill_a_basket_volunteer1_holds_a_lot_reservation() throws Exception {
        volunteer1BasketId = currentBasketId(volunteer1Session);
        addItem(volunteer1Session, volunteer1BasketId, STANDALONE_A_BARCODE);
        BasketDto v1AfterLot = addItem(volunteer1Session, volunteer1BasketId, LOT_MEMBER_BARCODE);
        lotId = v1AfterLot.lotGroups().get(0).lotId();

        volunteer2BasketId = currentBasketId(volunteer2Session);
        addItem(volunteer2Session, volunteer2BasketId, STANDALONE_B_BARCODE);

        assertThat(basketItemRepository.findAllByBasketIdOrderById(volunteer1BasketId)).hasSize(2);
        assertThat(basketItemRepository.findAllByBasketIdOrderById(volunteer2BasketId)).hasSize(1);
        assertThat(lotRepository.findById(lotId).orElseThrow().getReservedByBasketId()).isEqualTo(volunteer1BasketId);
    }

    @Test
    @Order(3)
    void volunteer1_logout_cancels_only_their_basket_and_releases_the_reservation() throws Exception {
        mockMvc.perform(post("/api/auth/logout").session(volunteer1Session).with(csrf()))
                .andExpect(status().isOk());

        assertThat(basketRepository.findById(volunteer1BasketId)).isEmpty();
        assertThat(basketItemRepository.findAllByBasketIdOrderById(volunteer1BasketId)).isEmpty();
        assertThat(lotRepository.findById(lotId).orElseThrow().getReservedByBasketId()).isNull();

        // Guard-rail C1: the other cashier's basket is untouched.
        assertThat(basketRepository.findById(volunteer2BasketId)).isPresent();
        assertThat(basketItemRepository.findAllByBasketIdOrderById(volunteer2BasketId)).hasSize(1);
    }

    @Test
    @Order(4)
    void a_repeated_logout_and_a_logout_without_a_basket_still_return_200() throws Exception {
        // volunteer1's session is already invalidated — logout must not NPE on the missing basket.
        mockMvc.perform(post("/api/auth/logout").session(volunteer1Session).with(csrf()))
                .andExpect(status().isOk());
        // admin has no POS basket at all — the handler resolves the active edition, finds nothing, no-ops.
        mockMvc.perform(post("/api/auth/logout").session(adminSession).with(csrf()))
                .andExpect(status().isOk());
    }

    private Long currentBasketId(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/pos/baskets/current").session(session))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), BasketDto.class).id();
    }

    private BasketDto addItem(MockHttpSession session, Long basketId, String barcode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/pos/baskets/" + basketId + "/items")
                        .session(session).with(csrf())
                        .param("barcode", barcode))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), BasketDto.class);
    }
}
