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
import org.pluribourse.domain.item.dto.ItemCompletenessDto;
import org.pluribourse.domain.item.dto.ItemDto;
import org.pluribourse.domain.item.dto.LotDto;
import org.pluribourse.domain.item.entity.Item;
import org.pluribourse.domain.item.repository.ItemRepository;
import org.pluribourse.domain.pos.dto.ScanResultDto;
import org.pluribourse.domain.seller.dto.SellerDto;
import org.pluribourse.shared.IntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 4.1 — POS scan lookup. A single seller/two items are created so their edition-scoped
 * numbers are deterministic (seller number 1, item numbers 1 and 2) — the real barcodes
 * ("00010001"/"00010002") are derived from this order rather than round-tripping through the
 * catalog endpoint, same convention as {@link org.pluribourse.domain.item.ItemCatalogIT}.
 * <p>
 * Order matters: the sale-phase guard (AC 9) is asserted <b>before</b> advancing to the Sale
 * phase, not after and rolled back — this project's phase transitions are one-directional in
 * these E2E scenarios.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PosScanIT extends IntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ItemRepository itemRepository;

    private MockHttpSession adminSession;
    private MockHttpSession volunteerSession;
    private MockHttpSession sellerSession;
    private Long editionId;
    private Long categoryId;
    private Long itemFirstId;
    private Long itemSecondId;
    private Long lotFirstMemberId;
    private Long lotSecondMemberId;

    private static final String FIRST_BARCODE = "00010001";
    private static final String SECOND_BARCODE = "00010002";
    private static final String LOT_FIRST_MEMBER_BARCODE = "00010003";
    private static final String LOT_SECOND_MEMBER_BARCODE = "00010004";

    @org.junit.jupiter.api.BeforeAll
    void setUpSessions() throws Exception {
        MvcResult adminLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "test_admin")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andReturn();
        adminSession = (MockHttpSession) adminLogin.getRequest().getSession(false);

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
    void create_edition_with_category() throws Exception {
        MvcResult editionResult = mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditionDto(null, "Bourse POS 2026", null, null, null, null, false, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3), null, null))))
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
    void advance_to_deposit_and_create_seller_with_two_items() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("DEPOSIT"));

        SellerDto sellerPayload = new SellerDto(null, "Alice", "Vendeuse", "alice.vendeuse@email.com", "0600000000");
        MvcResult sellerResult = mockMvc.perform(post("/api/sellers")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sellerPayload)))
                .andExpect(status().isCreated())
                .andReturn();
        Long sellerId = objectMapper.readValue(sellerResult.getResponse().getContentAsString(), SellerDto.class).id();

        CreateItemDto firstItem = new CreateItemDto(sellerId, categoryId, "Kapla", new java.math.BigDecimal("5.00"), false, null);
        MvcResult firstResult = mockMvc.perform(post("/api/items")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstItem)))
                .andExpect(status().isCreated())
                .andReturn();
        itemFirstId = objectMapper.readValue(firstResult.getResponse().getContentAsString(), ItemDto.class).id();

        CreateItemDto secondItem = new CreateItemDto(sellerId, categoryId, "Puzzle", new java.math.BigDecimal("3.00"), false, null);
        MvcResult secondResult = mockMvc.perform(post("/api/items")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondItem)))
                .andExpect(status().isCreated())
                .andReturn();
        itemSecondId = objectMapper.readValue(secondResult.getResponse().getContentAsString(), ItemDto.class).id();

        // A 2-member lot (item numbers 3 & 4) kept for the FR-109 lot-already-sold scan scenario
        // (Order 13) — lots can only be created in the Deposit phase, so it is built here.
        CreateLotDto lotPayload = new CreateLotDto(sellerId, categoryId, "Lot Duo", new java.math.BigDecimal("12.00"), List.of(
                new CreateLotItemDto("Piece A", false, null),
                new CreateLotItemDto("Piece B", false, null)));
        MvcResult lotResult = mockMvc.perform(post("/api/lots")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lotPayload)))
                .andExpect(status().isCreated())
                .andReturn();
        LotDto lot = objectMapper.readValue(lotResult.getResponse().getContentAsString(), LotDto.class);
        lotFirstMemberId = lot.items().get(0).id();
        lotSecondMemberId = lot.items().get(1).id();
    }

    @Test
    @Order(3)
    void scanning_outside_sale_phase_is_rejected() throws Exception {
        mockMvc.perform(get("/api/pos/scan").session(volunteerSession).param("barcode", FIRST_BARCODE))
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
    void scanning_a_valid_barcode_returns_the_item() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/pos/scan").session(volunteerSession).param("barcode", FIRST_BARCODE))
                .andExpect(status().isOk())
                .andReturn();
        ScanResultDto scanResult = objectMapper.readValue(result.getResponse().getContentAsString(), ScanResultDto.class);
        assertThat(scanResult.itemId()).isEqualTo(itemFirstId);
        assertThat(scanResult.name()).isEqualTo("Kapla");
        assertThat(scanResult.price()).isEqualByComparingTo("5.00");
        assertThat(scanResult.incomplete()).isFalse();
        assertThat(scanResult.comment()).isNull();
    }

    @Test
    @Order(6)
    void scanning_a_well_formed_but_nonexistent_barcode_returns_404() throws Exception {
        mockMvc.perform(get("/api/pos/scan").session(volunteerSession).param("barcode", "99990001"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(endsWith("/item-not-found")));
    }

    @Test
    @Order(7)
    void scanning_a_malformed_barcode_returns_404() throws Exception {
        mockMvc.perform(get("/api/pos/scan").session(volunteerSession).param("barcode", "1234567"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(endsWith("/item-not-found")));
        mockMvc.perform(get("/api/pos/scan").session(volunteerSession).param("barcode", "ABCDEFGH"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(endsWith("/item-not-found")));
    }

    @Test
    @Order(8)
    void scanning_an_already_sold_item_returns_409() throws Exception {
        Item item = itemRepository.findById(itemFirstId).orElseThrow();
        item.setSold(true);
        itemRepository.saveAndFlush(item);

        mockMvc.perform(get("/api/pos/scan").session(volunteerSession).param("barcode", FIRST_BARCODE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(endsWith("/item-already-sold")));
    }

    @Test
    @Order(9)
    void scanning_an_incomplete_item_returns_the_missing_detail() throws Exception {
        ItemCompletenessDto completenessPayload = new ItemCompletenessDto(true, "Manque une pièce");
        mockMvc.perform(patch("/api/items/" + itemSecondId)
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completenessPayload)))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/pos/scan").session(volunteerSession).param("barcode", SECOND_BARCODE))
                .andExpect(status().isOk())
                .andReturn();
        ScanResultDto scanResult = objectMapper.readValue(result.getResponse().getContentAsString(), ScanResultDto.class);
        assertThat(scanResult.incomplete()).isTrue();
        assertThat(scanResult.comment()).isEqualTo("Manque une pièce");
    }

    @Test
    @Order(10)
    void admin_can_also_scan() throws Exception {
        mockMvc.perform(get("/api/pos/scan").session(adminSession).param("barcode", SECOND_BARCODE))
                .andExpect(status().isOk());
    }

    @Test
    @Order(11)
    void seller_role_cannot_scan() throws Exception {
        mockMvc.perform(get("/api/pos/scan").session(sellerSession).param("barcode", SECOND_BARCODE))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(12)
    void unauthenticated_request_returns_401() throws Exception {
        mockMvc.perform(get("/api/pos/scan").param("barcode", SECOND_BARCODE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(13)
    void scanning_a_sibling_of_an_already_sold_lot_returns_409() throws Exception {
        // FR-109 (story 5.8): mark one lot member sold directly (same technique as Order 8), then
        // scan the other — a lot is sold at most once, so its remaining members are unreachable.
        Item lotMember = itemRepository.findById(lotFirstMemberId).orElseThrow();
        lotMember.setSold(true);
        itemRepository.saveAndFlush(lotMember);

        mockMvc.perform(get("/api/pos/scan").session(volunteerSession).param("barcode", LOT_SECOND_MEMBER_BARCODE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(endsWith("/lot-already-sold")));

        assertThat(itemRepository.findById(lotSecondMemberId).orElseThrow().isSold()).isFalse();
    }
}
