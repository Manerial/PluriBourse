package org.pluribourse.domain.archive;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.pluribourse.domain.archive.entity.ArchivedItem;
import org.pluribourse.domain.archive.repository.ArchivedItemRepository;
import org.pluribourse.domain.edition.dto.EditionCategoryDto;
import org.pluribourse.domain.edition.dto.EditionDto;
import org.pluribourse.domain.item.dto.CreateItemDto;
import org.pluribourse.domain.item.dto.CreateLotDto;
import org.pluribourse.domain.item.dto.CreateLotItemDto;
import org.pluribourse.domain.item.repository.ItemRepository;
import org.pluribourse.domain.payout.dto.SettleDto;
import org.pluribourse.domain.payout.entity.Settlement;
import org.pluribourse.domain.payout.entity.SettlementStatus;
import org.pluribourse.domain.payout.repository.SettlementRepository;
import org.pluribourse.domain.pos.dto.BasketDto;
import org.pluribourse.domain.pos.dto.ValidateBasketDto;
import org.pluribourse.domain.pos.entity.PaymentMethod;
import org.pluribourse.domain.report.dto.EditionSummaryReportDto;
import org.pluribourse.domain.seller.dto.SellerDto;
import org.pluribourse.domain.seller.repository.SellerRepository;
import org.pluribourse.domain.user.enums.Language;
import org.pluribourse.shared.IntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 2.7 (AC 5, 6, 7, FR-088): edition archiving — item records copied into
 * {@code archived_items} (lot members archived individually, no lot reference retained), then
 * {@code items}/{@code seller_profiles}/{@code settlements} permanently deleted, with the edition
 * report snapshot frozen beforehand so it stays readable afterwards. Alice sells one item and
 * keeps one unsold; Bob deposits a 2-member lot with only one member ever sold, proving the
 * per-item {@code sold} flag (not the lot-aware "1 per lot" count used elsewhere) is what gets
 * archived. Alice is settled before closing, Bob is left UNSETTLED so that closing (the
 * dedicated {@code /close} endpoint, FR-096) auto-marks him Non réclamé — together they exercise
 * the settlements-before-sellers deletion order (FK has no cascade, see the story's Dev Notes §
 * FK ordering) for both a manually settled and an auto-settled seller.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EditionArchivingIT extends IntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private SellerRepository sellerRepository;
    @Autowired
    private SettlementRepository settlementRepository;
    @Autowired
    private ArchivedItemRepository archivedItemRepository;

    private static final String EDITION_NAME = "Bourse Archive 2026";
    private static final String ALICE_SOLD_BARCODE = "00010001"; // 10.00€
    // Alice's second item (5.00€) is never scanned — stays unsold.
    private static final String LOT_DUO_MEMBER_A_BARCODE = "00020001"; // Lot Duo, only member scanned, CARD

    private MockHttpSession adminSession;
    private MockHttpSession volunteer1Session;
    private Long editionId;
    private Long categoryId;
    private Long aliceId;
    private Long emptyEditionId;

    @Test
    @Order(1)
    void log_in_admin_and_volunteer() throws Exception {
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
    @Order(2)
    void create_edition_and_advance_to_deposit_phase() throws Exception {
        MvcResult editionResult = mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditionDto(null, EDITION_NAME,
                                null, new BigDecimal("10.00"), Language.FR, null, false,
                                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3), null, "€"))))
                .andExpect(status().isCreated())
                .andReturn();
        editionId = objectMapper.readValue(editionResult.getResponse().getContentAsString(), EditionDto.class).id();

        MvcResult categoriesResult = mockMvc.perform(put("/api/admin/editions/" + editionId + "/categories")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(new EditionCategoryDto(null, "Jouets", List.of(1))))))
                .andExpect(status().isOk())
                .andReturn();
        categoryId = objectMapper.readValue(categoriesResult.getResponse().getContentAsString(),
                new TypeReference<List<EditionCategoryDto>>() {
                }).get(0).id();

        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("DEPOSIT"));
    }

    @Test
    @Order(3)
    void create_alice_and_bob_with_a_sold_item_an_unsold_item_and_a_partially_sold_lot() throws Exception {
        aliceId = createSeller("Alice", "Vendeuse", "alice.archive@email.com", "0600000001");
        Long bobId = createSeller("Bob", "Vendeur", "bob.archive@email.com", "0600000002");

        mockMvc.perform(post("/api/items")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateItemDto(aliceId, categoryId, "Article Alice", new BigDecimal("10.00"), false, null))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/items")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateItemDto(aliceId, categoryId, "Article Alice Invendu", new BigDecimal("5.00"), false, null))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/lots")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateLotDto(bobId, categoryId, "Lot Duo", new BigDecimal("8.00"),
                                List.of(new CreateLotItemDto("Duo A", false, null),
                                        new CreateLotItemDto("Duo B", false, null))))))
                .andExpect(status().isCreated());
    }

    @Test
    @Order(4)
    void advance_to_sale_and_sell_alices_item_and_one_lot_member() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("SALE"));

        sellOneItem(ALICE_SOLD_BARCODE, PaymentMethod.CASH, "10.00");
        sellOneItem(LOT_DUO_MEMBER_A_BARCODE, PaymentMethod.CARD, "8.00");
    }

    @Test
    @Order(5)
    void advance_to_post_sale_and_settle_alice() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("POST_SALE"));

        // Alice: 10.00 - 10% commission = 9.00€ due, settled in full. Bob stays UNSETTLED here —
        // closing the edition below (Order 7) auto-marks him Non réclamé (FR-096).
        mockMvc.perform(post("/api/settlements/" + aliceId + "/settle")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SettleDto(new BigDecimal("9.00")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"));
    }

    @Test
    @Order(6)
    void archive_while_not_closed_returns_422() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/archive")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(endsWith("/edition-not-closed")));
    }

    /**
     * Closes via the dedicated {@code /close} endpoint, not the generic {@code /phase/advance}
     * (which now refuses POST_SALE → CLOSED — see {@code ClosingRequiresDedicatedEndpointException},
     * the FR-096 follow-up fix) — this is what actually settles Bob, still UNSETTLED at this point.
     */
    @Test
    @Order(7)
    void closing_the_edition_marks_bob_unclaimed() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/close")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("CLOSED"));

        Settlement bobSettlement = settlementRepository.findAllBySellerProfileEditionId(editionId).stream()
                .filter(s -> !s.getSellerProfile().getId().equals(aliceId))
                .findFirst().orElseThrow();
        assertThat(bobSettlement.getStatus()).isEqualTo(SettlementStatus.UNCLAIMED);
    }

    /**
     * Follow-up fix (2026-08-23): archiving a zero-item edition used to return 422
     * no-items-to-archive (Story 2.7 AC5 / UX-DR18) — a Clôturée edition with no deposited items
     * (e.g. sellers registered but never deposited) had no way to purge its seller profiles or
     * freeze its report snapshot. The 0-items signal moved earlier, as a non-blocking warning on
     * the DEPOSIT → SALE transition (PhaseControlComponent) instead of a hard block at archive time.
     */
    @Test
    @Order(8)
    void archiving_an_edition_with_no_items_succeeds_and_archives_nothing() throws Exception {
        MvcResult editionResult = mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditionDto(null, "Bourse Archive Vide 2026",
                                null, new BigDecimal("10.00"), Language.FR, null, false,
                                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 3), null, "€"))))
                .andExpect(status().isCreated())
                .andReturn();
        emptyEditionId = objectMapper.readValue(editionResult.getResponse().getContentAsString(), EditionDto.class).id();

        mockMvc.perform(put("/api/admin/editions/" + emptyEditionId + "/categories")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(new EditionCategoryDto(null, "Jouets", List.of(1))))))
                .andExpect(status().isOk());

        // PREPARATION -> DEPOSIT -> SALE -> POST_SALE, no seller/item ever created.
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/admin/editions/" + emptyEditionId + "/phase/advance")
                            .session(adminSession).with(csrf()))
                    .andExpect(status().isOk());
        }
        // POST_SALE -> CLOSED goes through the dedicated /close endpoint (no sellers to settle here).
        mockMvc.perform(post("/api/admin/editions/" + emptyEditionId + "/close")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/editions/" + emptyEditionId + "/archive")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));

        assertThat(archivedItemRepository.findAllByEditionId(emptyEditionId)).isEmpty();
    }

    @Test
    @Order(9)
    void volunteer_cannot_archive() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/archive")
                        .session(volunteer1Session).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(10)
    void archiving_copies_items_deletes_records_and_preserves_the_report() throws Exception {
        EditionSummaryReportDto beforeArchive = getEditionReport();

        mockMvc.perform(post("/api/admin/editions/" + editionId + "/archive")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));

        List<ArchivedItem> archived = archivedItemRepository.findAllByEditionId(editionId);
        assertThat(archived).hasSize(4);
        assertThat(archived).extracting(ArchivedItem::getName, ArchivedItem::getCategoryName, ArchivedItem::isSold)
                .containsExactlyInAnyOrder(
                        tuple("Article Alice", "Jouets", true),
                        tuple("Article Alice Invendu", "Jouets", false),
                        tuple("Duo A", "Jouets", true),
                        tuple("Duo B", "Jouets", false));

        assertThat(itemRepository.existsByEditionId(editionId)).isFalse();
        assertThat(sellerRepository.findAllByEditionId(editionId)).isEmpty();
        assertThat(settlementRepository.findAllBySellerProfileEditionId(editionId)).isEmpty();

        EditionSummaryReportDto afterArchive = getEditionReport();
        assertThat(afterArchive).usingRecursiveComparison().isEqualTo(beforeArchive);
    }

    @Test
    @Order(11)
    void rollback_after_archiving_returns_422() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/rollback")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(endsWith("/phase-rollback-disabled-after-archive")));
    }

    @Test
    @Order(12)
    void archiving_an_already_archived_edition_returns_422() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/archive")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(endsWith("/edition-already-archived")));
    }

    private Long createSeller(String firstName, String lastName, String email, String phone) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/sellers")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SellerDto(null, firstName, lastName, email, phone))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), SellerDto.class).id();
    }

    private void sellOneItem(String barcode, PaymentMethod paymentMethod, String price) throws Exception {
        Long basketId = currentBasketId();
        mockMvc.perform(post("/api/pos/baskets/" + basketId + "/items")
                        .session(volunteer1Session).with(csrf())
                        .param("barcode", barcode))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/pos/baskets/" + basketId + "/validate")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ValidateBasketDto(paymentMethod,
                                paymentMethod == PaymentMethod.CASH ? new BigDecimal(price) : null))))
                .andExpect(status().isOk());
    }

    private Long currentBasketId() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/pos/baskets/current").session(volunteer1Session))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), BasketDto.class).id();
    }

    private EditionSummaryReportDto getEditionReport() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/admin/reports/edition/" + editionId).session(adminSession))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), EditionSummaryReportDto.class);
    }
}
