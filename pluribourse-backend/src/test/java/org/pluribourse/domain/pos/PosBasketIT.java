package org.pluribourse.domain.pos;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.pluribourse.domain.edition.dto.EditionCategoryDto;
import org.pluribourse.domain.edition.dto.EditionDto;
import org.pluribourse.domain.item.dto.CreateItemDto;
import org.pluribourse.domain.item.dto.CreateLotDto;
import org.pluribourse.domain.item.dto.CreateLotItemDto;
import org.pluribourse.domain.item.dto.ItemDto;
import org.pluribourse.domain.item.dto.LotDto;
import org.pluribourse.domain.item.entity.Item;
import org.pluribourse.domain.item.repository.ItemRepository;
import org.pluribourse.domain.pos.dto.BasketDto;
import org.pluribourse.domain.pos.dto.SaleDto;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 4.2/4.3 — persisted POS basket, payment validation & lot handling. Alice (seller 1) gets 4
 * individual items (barcodes 0001-0003 for the shopping-cart scenarios, 0006 reserved untouched
 * for the end-of-class phase-guard scenario) plus a 2-item lot (barcodes 0004-0005, global price
 * 10.00) to prove the lot-aware total, and a second 2-item lot (barcodes 0008-0009, global price
 * 6.00) used only by the lot-removal scenario. Bob (seller 2) gets a single item used only by the
 * concurrency scenario.
 * <p>
 * Order matters: phase transitions are one-directional in these E2E scenarios (cf. {@link PosScanIT}) —
 * the class ends in POST_SALE, so any scenario needing the Sale phase must run before {@link #phase_guard_rejects_four_endpoints_and_the_cancelled_basket_404s_add_item()}.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PosBasketIT extends IntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ItemRepository itemRepository;

    private MockHttpSession adminSession;
    private MockHttpSession volunteer1Session;
    private MockHttpSession volunteer2Session;
    private MockHttpSession sellerSession;
    private Long editionId;
    private Long categoryId;

    private static final String ITEM_1_BARCODE = "00010001"; // Kapla, 5.00
    private static final String ITEM_2_BARCODE = "00010002"; // Puzzle, 3.00
    private static final String ITEM_3_BARCODE = "00010003"; // Extra, 2.00
    private static final String LOT_ITEM_1_BARCODE = "00010004";
    private static final String LOT_ITEM_2_BARCODE = "00010005";
    private static final String ITEM_6_BARCODE = "00010006"; // Backup, 1.50 — untouched until phase-guard scenario
    private static final String ITEM_7_BARCODE = "00010007"; // Insuffisant, 4.00 — insufficient-amount scenario only
    private static final String LOT2_ITEM_1_BARCODE = "00010008"; // Lot Retrait, 6.00 — removal scenario only
    private static final String LOT2_ITEM_2_BARCODE = "00010009";
    private static final String BOB_ITEM_BARCODE = "00020001"; // Boardgame, 6.00 — concurrency scenario only

    private Long item1Id;
    private Long volunteer1BasketId;
    private Long lotJouetsId;

    @org.junit.jupiter.api.BeforeAll
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
    void create_edition_with_category() throws Exception {
        MvcResult editionResult = mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditionDto(null, "Bourse Panier 2026", null, null, null, null, false, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3), null, null))))
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
        categoryId = objectMapper.readValue(
                categoriesResult.getResponse().getContentAsString(), new TypeReference<List<EditionCategoryDto>>() {
                }).get(0).id();
    }

    @Test
    @Order(2)
    void advance_to_deposit_and_create_sellers_with_items_and_a_lot() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("DEPOSIT"));

        SellerDto alicePayload = new SellerDto(null, "Alice", "Vendeuse", "alice.panier@email.com", "0600000001");
        MvcResult aliceResult = mockMvc.perform(post("/api/sellers")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(alicePayload)))
                .andExpect(status().isCreated())
                .andReturn();
        Long aliceId = objectMapper.readValue(aliceResult.getResponse().getContentAsString(), SellerDto.class).id();

        item1Id = createItem(aliceId, "Kapla", "5.00");
        createItem(aliceId, "Puzzle", "3.00");
        createItem(aliceId, "Extra", "2.00");

        CreateLotDto lotPayload = new CreateLotDto(aliceId, categoryId, "Lot Jouets", new BigDecimal("10.00"),
                List.of(new CreateLotItemDto("Lot item A", false, null),
                        new CreateLotItemDto("Lot item B", false, null)));
        mockMvc.perform(post("/api/lots")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lotPayload)))
                .andExpect(status().isCreated())
                .andReturn();

        createItem(aliceId, "Backup", "1.50");
        createItem(aliceId, "Insuffisant", "4.00");

        CreateLotDto lot2Payload = new CreateLotDto(aliceId, categoryId, "Lot Retrait", new BigDecimal("6.00"),
                List.of(new CreateLotItemDto("Lot retrait item A", false, null),
                        new CreateLotItemDto("Lot retrait item B", false, null)));
        mockMvc.perform(post("/api/lots")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lot2Payload)))
                .andExpect(status().isCreated())
                .andReturn();

        SellerDto bobPayload = new SellerDto(null, "Bob", "Vendeur", "bob.panier@email.com", "0600000002");
        MvcResult bobResult = mockMvc.perform(post("/api/sellers")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bobPayload)))
                .andExpect(status().isCreated())
                .andReturn();
        Long bobId = objectMapper.readValue(bobResult.getResponse().getContentAsString(), SellerDto.class).id();
        createItem(bobId, "Boardgame", "6.00");
    }

    private Long createItem(Long sellerId, String name, String price) throws Exception {
        CreateItemDto payload = new CreateItemDto(sellerId, categoryId, name, new BigDecimal(price), false, null);
        MvcResult result = mockMvc.perform(post("/api/items")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), ItemDto.class).id();
    }

    @Test
    @Order(3)
    void basket_endpoint_rejected_outside_sale_phase() throws Exception {
        mockMvc.perform(get("/api/pos/baskets/current").session(volunteer1Session))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(endsWith("/sale-phase-required")));
    }

    @Test
    @Order(4)
    void advance_to_sale_phase() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("SALE"));
    }

    @Test
    @Order(5)
    void current_basket_is_created_once_and_reused() throws Exception {
        MvcResult first = mockMvc.perform(get("/api/pos/baskets/current").session(volunteer1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andReturn();
        BasketDto firstBasket = objectMapper.readValue(first.getResponse().getContentAsString(), BasketDto.class);
        assertThat(firstBasket.total()).isEqualByComparingTo("0");
        volunteer1BasketId = firstBasket.id();

        MvcResult second = mockMvc.perform(get("/api/pos/baskets/current").session(volunteer1Session))
                .andExpect(status().isOk())
                .andReturn();
        BasketDto secondBasket = objectMapper.readValue(second.getResponse().getContentAsString(), BasketDto.class);
        assertThat(secondBasket.id()).isEqualTo(volunteer1BasketId);
    }

    @Test
    @Order(6)
    void adding_items_computes_a_lot_aware_total() throws Exception {
        BasketDto afterFirstItem = addItem(volunteer1Session, volunteer1BasketId, ITEM_1_BARCODE);
        assertThat(afterFirstItem.total()).isEqualByComparingTo("5.00");

        BasketDto afterFirstLotItem = addItem(volunteer1Session, volunteer1BasketId, LOT_ITEM_1_BARCODE);
        assertThat(afterFirstLotItem.total()).isEqualByComparingTo("15.00");
        assertThat(afterFirstLotItem.lotGroups()).hasSize(1);
        assertThat(afterFirstLotItem.lotGroups().get(0).scannedCount()).isEqualTo(1);
        assertThat(afterFirstLotItem.lotGroups().get(0).totalCount()).isEqualTo(2);
        assertThat(afterFirstLotItem.lotGroups().get(0).globalPrice()).isEqualByComparingTo("10.00");
        lotJouetsId = afterFirstLotItem.lotGroups().get(0).lotId();

        // The lot's global price must be counted once, not twice, once its second member joins too.
        BasketDto afterSecondLotItem = addItem(volunteer1Session, volunteer1BasketId, LOT_ITEM_2_BARCODE);
        assertThat(afterSecondLotItem.total()).isEqualByComparingTo("15.00");
        assertThat(afterSecondLotItem.items()).hasSize(3);
        assertThat(afterSecondLotItem.lotGroups().get(0).scannedCount()).isEqualTo(2);
    }

    private BasketDto addItem(MockHttpSession session, Long basketId, String barcode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/pos/baskets/" + basketId + "/items")
                        .session(session).with(csrf())
                        .param("barcode", barcode))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), BasketDto.class);
    }

    @Test
    @Order(7)
    void re_adding_the_same_item_is_rejected() throws Exception {
        mockMvc.perform(post("/api/pos/baskets/" + volunteer1BasketId + "/items")
                        .session(volunteer1Session).with(csrf())
                        .param("barcode", ITEM_1_BARCODE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(endsWith("/item-already-in-basket")));
    }

    @Test
    @Order(8)
    void removing_an_item_recalculates_the_total() throws Exception {
        MvcResult result = mockMvc.perform(delete("/api/pos/baskets/" + volunteer1BasketId + "/items/" + item1Id)
                        .session(volunteer1Session).with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        BasketDto basket = objectMapper.readValue(result.getResponse().getContentAsString(), BasketDto.class);
        assertThat(basket.total()).isEqualByComparingTo("10.00");
        assertThat(basket.items()).extracting("itemId").doesNotContain(item1Id);
    }

    @Test
    @Order(9)
    void removing_an_absent_item_returns_404() throws Exception {
        mockMvc.perform(delete("/api/pos/baskets/" + volunteer1BasketId + "/items/" + item1Id)
                        .session(volunteer1Session).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(endsWith("/basket-item-not-found")));
    }

    @Test
    @Order(10)
    void another_volunteers_basket_is_never_reachable_ownership_is_never_confirmed_or_denied() throws Exception {
        mockMvc.perform(post("/api/pos/baskets/" + volunteer1BasketId + "/items")
                        .session(volunteer2Session).with(csrf())
                        .param("barcode", ITEM_2_BARCODE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(endsWith("/basket-not-found")));
    }

    @Test
    @Order(11)
    void validating_with_exact_cash_amount_sells_the_remaining_lot_items() throws Exception {
        ValidateBasketDto payload = new ValidateBasketDto(PaymentMethod.CASH, null);
        MvcResult result = mockMvc.perform(post("/api/pos/baskets/" + volunteer1BasketId + "/validate")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn();
        SaleDto sale = objectMapper.readValue(result.getResponse().getContentAsString(), SaleDto.class);
        assertThat(sale.total()).isEqualByComparingTo("10.00");
        assertThat(sale.changeDue()).isNull();

        Item firstLotItem = itemRepository.findByEditionIdAndSellerNumberAndItemNumber(editionId, 1, 4).orElseThrow();
        Item secondLotItem = itemRepository.findByEditionIdAndSellerNumberAndItemNumber(editionId, 1, 5).orElseThrow();
        assertThat(firstLotItem.isSold()).isTrue();
        assertThat(firstLotItem.getSale()).isNotNull();
        assertThat(secondLotItem.isSold()).isTrue();
        Item removedItem = itemRepository.findById(item1Id).orElseThrow();
        assertThat(removedItem.isSold()).isFalse();
    }

    @Test
    @Order(12)
    void validating_creates_a_fresh_empty_basket_for_the_next_transaction() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/pos/baskets/current").session(volunteer1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andReturn();
        BasketDto basket = objectMapper.readValue(result.getResponse().getContentAsString(), BasketDto.class);
        assertThat(basket.id()).isNotEqualTo(volunteer1BasketId);
        volunteer1BasketId = basket.id();
    }

    @Test
    @Order(13)
    void validating_with_cash_above_total_computes_the_change_due() throws Exception {
        addItem(volunteer1Session, volunteer1BasketId, ITEM_2_BARCODE);

        ValidateBasketDto payload = new ValidateBasketDto(PaymentMethod.CASH, new BigDecimal("5.00"));
        MvcResult result = mockMvc.perform(post("/api/pos/baskets/" + volunteer1BasketId + "/validate")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn();
        SaleDto sale = objectMapper.readValue(result.getResponse().getContentAsString(), SaleDto.class);
        assertThat(sale.changeDue()).isEqualByComparingTo("2.00");

        MvcResult next = mockMvc.perform(get("/api/pos/baskets/current").session(volunteer1Session))
                .andExpect(status().isOk())
                .andReturn();
        volunteer1BasketId = objectMapper.readValue(next.getResponse().getContentAsString(), BasketDto.class).id();
    }

    @Test
    @Order(14)
    void validating_with_card_never_requires_an_amount() throws Exception {
        addItem(volunteer1Session, volunteer1BasketId, ITEM_3_BARCODE);

        ValidateBasketDto payload = new ValidateBasketDto(PaymentMethod.CARD, null);
        mockMvc.perform(post("/api/pos/baskets/" + volunteer1BasketId + "/validate")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        MvcResult next = mockMvc.perform(get("/api/pos/baskets/current").session(volunteer1Session))
                .andExpect(status().isOk())
                .andReturn();
        volunteer1BasketId = objectMapper.readValue(next.getResponse().getContentAsString(), BasketDto.class).id();
    }

    @Test
    @Order(15)
    void validating_with_check_never_requires_an_amount() throws Exception {
        addItem(volunteer1Session, volunteer1BasketId, ITEM_1_BARCODE);

        ValidateBasketDto payload = new ValidateBasketDto(PaymentMethod.CHECK, null);
        MvcResult result = mockMvc.perform(post("/api/pos/baskets/" + volunteer1BasketId + "/validate")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn();
        SaleDto sale = objectMapper.readValue(result.getResponse().getContentAsString(), SaleDto.class);
        assertThat(sale.paymentMethod()).isEqualTo(PaymentMethod.CHECK);
        assertThat(sale.changeDue()).isNull();

        MvcResult next = mockMvc.perform(get("/api/pos/baskets/current").session(volunteer1Session))
                .andExpect(status().isOk())
                .andReturn();
        volunteer1BasketId = objectMapper.readValue(next.getResponse().getContentAsString(), BasketDto.class).id();
    }

    @Test
    @Order(16)
    void validating_cash_with_insufficient_amount_is_rejected() throws Exception {
        addItem(volunteer1Session, volunteer1BasketId, ITEM_7_BARCODE);

        ValidateBasketDto payload = new ValidateBasketDto(PaymentMethod.CASH, new BigDecimal("3.00"));
        mockMvc.perform(post("/api/pos/baskets/" + volunteer1BasketId + "/validate")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(endsWith("/invalid-amount-given")));

        Item item = itemRepository.findByEditionIdAndSellerNumberAndItemNumber(editionId, 1, 7).orElseThrow();
        assertThat(item.isSold()).isFalse();

        // Cleans up so the next scenario (empty-basket rejection) starts from a truly empty basket.
        mockMvc.perform(delete("/api/pos/baskets/" + volunteer1BasketId + "/items/" + item.getId())
                        .session(volunteer1Session).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @Order(17)
    void validating_an_empty_basket_is_rejected() throws Exception {
        ValidateBasketDto payload = new ValidateBasketDto(PaymentMethod.CASH, null);
        mockMvc.perform(post("/api/pos/baskets/" + volunteer1BasketId + "/validate")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(endsWith("/basket-empty")));
    }

    @Test
    @Order(18)
    void validating_without_a_payment_method_is_a_bean_validation_failure() throws Exception {
        mockMvc.perform(post("/api/pos/baskets/" + volunteer1BasketId + "/validate")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("/validation-failed")));
    }

    @Test
    @Order(19)
    void removing_the_entire_lot_removes_all_its_items() throws Exception {
        MvcResult v2CurrentResult = mockMvc.perform(get("/api/pos/baskets/current").session(volunteer2Session))
                .andReturn();
        Long v2BasketId = objectMapper.readValue(v2CurrentResult.getResponse().getContentAsString(), BasketDto.class).id();

        BasketDto afterFirst = addItem(volunteer2Session, v2BasketId, LOT2_ITEM_1_BARCODE);
        assertThat(afterFirst.lotGroups()).hasSize(1);
        assertThat(afterFirst.lotGroups().get(0).scannedCount()).isEqualTo(1);
        assertThat(afterFirst.lotGroups().get(0).totalCount()).isEqualTo(2);
        Long lot2Id = afterFirst.lotGroups().get(0).lotId();

        BasketDto afterSecond = addItem(volunteer2Session, v2BasketId, LOT2_ITEM_2_BARCODE);
        assertThat(afterSecond.lotGroups().get(0).scannedCount()).isEqualTo(2);
        assertThat(afterSecond.total()).isEqualByComparingTo("6.00");

        MvcResult removeResult = mockMvc.perform(delete("/api/pos/baskets/" + v2BasketId + "/lots/" + lot2Id)
                        .session(volunteer2Session).with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        BasketDto afterRemove = objectMapper.readValue(removeResult.getResponse().getContentAsString(), BasketDto.class);
        assertThat(afterRemove.items()).isEmpty();
        assertThat(afterRemove.lotGroups()).isEmpty();
        assertThat(afterRemove.total()).isEqualByComparingTo("0");

        mockMvc.perform(delete("/api/pos/baskets/" + v2BasketId + "/lots/" + lot2Id)
                        .session(volunteer2Session).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(endsWith("/basket-lot-not-found")));
    }

    @Test
    @Order(20)
    void a_sale_conflict_is_detected_at_validation_not_at_scan() throws Exception {
        MvcResult v1CurrentResult = mockMvc.perform(get("/api/pos/baskets/current").session(volunteer1Session))
                .andReturn();
        Long v1BasketId = objectMapper.readValue(v1CurrentResult.getResponse().getContentAsString(), BasketDto.class).id();
        MvcResult v2CurrentResult = mockMvc.perform(get("/api/pos/baskets/current").session(volunteer2Session))
                .andReturn();
        Long v2BasketId = objectMapper.readValue(v2CurrentResult.getResponse().getContentAsString(), BasketDto.class).id();

        // Both volunteers can scan the same not-yet-sold item into their own basket — the
        // conflict is not detected here (architecture § Concurrence POS).
        addItem(volunteer1Session, v1BasketId, BOB_ITEM_BARCODE);
        addItem(volunteer2Session, v2BasketId, BOB_ITEM_BARCODE);

        ValidateBasketDto payload = new ValidateBasketDto(PaymentMethod.CASH, null);
        mockMvc.perform(post("/api/pos/baskets/" + v1BasketId + "/validate")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        MvcResult conflictResult = mockMvc.perform(post("/api/pos/baskets/" + v2BasketId + "/validate")
                        .session(volunteer2Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(endsWith("/basket-validation-conflict")))
                .andExpect(jsonPath("$.conflictingItems[0].name").value("Boardgame"))
                .andReturn();
        assertThat(conflictResult.getResponse().getContentAsString()).contains("Boardgame");

        Item bobItem = itemRepository.findByEditionIdAndSellerNumberAndItemNumber(editionId, 2, 1).orElseThrow();
        assertThat(bobItem.isSold()).isTrue();

        MvcResult freshBasket = mockMvc.perform(get("/api/pos/baskets/current").session(volunteer1Session)).andReturn();
        volunteer1BasketId = objectMapper.readValue(freshBasket.getResponse().getContentAsString(), BasketDto.class).id();
    }

    @Test
    @Order(21)
    void seller_role_is_forbidden_on_all_five_endpoints() throws Exception {
        mockMvc.perform(get("/api/pos/baskets/current").session(sellerSession))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/pos/baskets/" + volunteer1BasketId + "/items")
                        .session(sellerSession).with(csrf()).param("barcode", ITEM_6_BARCODE))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/pos/baskets/" + volunteer1BasketId + "/items/1")
                        .session(sellerSession).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/pos/baskets/" + volunteer1BasketId + "/lots/1")
                        .session(sellerSession).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/pos/baskets/" + volunteer1BasketId + "/validate")
                        .session(sellerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ValidateBasketDto(PaymentMethod.CASH, null))))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(22)
    void unauthenticated_request_returns_401() throws Exception {
        mockMvc.perform(get("/api/pos/baskets/current"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Critical scenario (AC 9): once story 2.8 landed, a basket no longer outlives a phase change —
     * the transition to POST_SALE deletes {@code volunteer1BasketId} server-side (all baskets of the
     * edition, story 2.8) before this scenario ever calls the five endpoints below. Four of them
     * ({@code GET current}, {@code removeItem}, {@code removeLot}, {@code validate}) check the Sale
     * phase guard before basket ownership, so they still 422 exactly as before 2.8 — the phase itself
     * is already wrong regardless of whether the basket still exists. {@code addItem} is the outlier:
     * it checks basket ownership first ({@code requireOwnedBasket}), so against a now-deleted basket
     * it 404s instead — a more accurate response than the pre-2.8 422, since the real reason is that
     * the basket was cancelled, not merely that the phase changed. Placed last because phase
     * transitions are one-directional in this test class.
     */
    @Test
    @Order(23)
    void phase_guard_rejects_four_endpoints_and_the_cancelled_basket_404s_add_item() throws Exception {
        addItem(volunteer1Session, volunteer1BasketId, ITEM_6_BARCODE);

        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("POST_SALE"));

        mockMvc.perform(get("/api/pos/baskets/current").session(volunteer1Session))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(endsWith("/sale-phase-required")));
        mockMvc.perform(post("/api/pos/baskets/" + volunteer1BasketId + "/items")
                        .session(volunteer1Session).with(csrf())
                        .param("barcode", ITEM_6_BARCODE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(endsWith("/basket-not-found")));
        mockMvc.perform(delete("/api/pos/baskets/" + volunteer1BasketId + "/items/1")
                        .session(volunteer1Session).with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(endsWith("/sale-phase-required")));
        mockMvc.perform(delete("/api/pos/baskets/" + volunteer1BasketId + "/lots/" + lotJouetsId)
                        .session(volunteer1Session).with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(endsWith("/sale-phase-required")));
        mockMvc.perform(post("/api/pos/baskets/" + volunteer1BasketId + "/validate")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ValidateBasketDto(PaymentMethod.CASH, null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(endsWith("/sale-phase-required")));
    }
}
