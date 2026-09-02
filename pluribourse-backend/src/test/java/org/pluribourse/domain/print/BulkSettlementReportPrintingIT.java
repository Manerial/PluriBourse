package org.pluribourse.domain.print;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.pluribourse.domain.edition.dto.EditionCategoryDto;
import org.pluribourse.domain.edition.dto.EditionDto;
import org.pluribourse.domain.item.dto.CreateItemDto;
import org.pluribourse.domain.item.entity.Item;
import org.pluribourse.domain.item.repository.ItemRepository;
import org.pluribourse.domain.payout.dto.BulkSettlementReportPrintResultDto;
import org.pluribourse.domain.payout.dto.SettleDto;
import org.pluribourse.domain.pos.dto.BasketDto;
import org.pluribourse.domain.pos.dto.ValidateBasketDto;
import org.pluribourse.domain.pos.entity.PaymentMethod;
import org.pluribourse.domain.print.dto.CreatePrinterDto;
import org.pluribourse.domain.print.dto.PrinterDto;
import org.pluribourse.domain.print.entity.PrinterType;
import org.pluribourse.domain.print.service.PrintQueueService;
import org.pluribourse.domain.print.service.SettlementReportRenderer;
import org.pluribourse.domain.seller.dto.SellerDto;
import org.pluribourse.domain.user.enums.Language;
import org.pluribourse.shared.IntegrationTest;
import org.pluribourse.shared.PrinterBridgeDouble;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 5.6: bulk settlement report printing (FR-097), admin-only. Distinct storyboard from
 * {@link SettlementReportPrintingIT} (single-seller printing, story 5.2, a one-seller scenario)
 * — testing the {@code SettlementFilter} server-side filter requires several sellers at
 * different settlement statuses from the start, which would mean restructuring that file's
 * already-stable Deposit/Sale phases (same reasoning already documented in story 5.5 for why
 * {@code ReportExportIT} is separate from {@code EditionReportPrintingIT}). Three sellers, each
 * settled differently in Post-vente: Alice stays UNSETTLED, Bob is SETTLED (full amount),
 * Carol is UNCLAIMED — covering the three {@code SettlementFilter} values distinctly (ALL,
 * UNSETTLED, SETTLED — the latter proving it groups SETTLED + UNCLAIMED, not just the persisted
 * SETTLED status). A fourth seller, David, is registered with zero items and also stays
 * UNSETTLED: proves the {@code itemsBySellerId.getOrDefault(seller.getId(), List.of())} fallback
 * in {@code SettlementReportPrintService.printAllReports} (a seller with no deposit is a
 * realistic case, never exercised by Alice/Bob/Carol who all have at least one item — code review
 * finding, 2026-08-20). Alice alone also keeps one unsold item, which combined with her one sold
 * item is enough to prove — by rendering directly with {@link SettlementReportRenderer}, same
 * pattern as {@code SettlementReportPrintingIT} Order 8 — that
 * {@code findAllByEditionIdForSettlementReport} returns data usable as-is by the same
 * renderer/{@code DocumentPrintService.buildSettlementReportJob} already proven correct there;
 * the FR-050 report format itself is not re-verified here.
 * <p>
 * The {@code failedCount > 0} branch (partial queueing failure) is a known, deliberately
 * untested limit (see Dev Notes in the story): {@link PrintQueueService#submit} only fails on the
 * narrow race of a printer being unregistered between the availability check and the current
 * iteration, which {@code MockMvc}'s synchronous execution cannot force deterministically —
 * mocking {@code PrintQueueService}/{@code DocumentPrintService} to force it would break this
 * codebase's E2E-by-controller testing philosophy for marginal coverage on an already-understood
 * path (same category as the undocumented-but-accepted races in {@code SaleConcurrencyIT}).
 * <p>
 * Known test limitation (code review, 2026-08-20): {@code SettlementService.getSellersMatchingFilter}'s
 * {@code sellerNumber} sort determines the order in which jobs are submitted to
 * {@code printQueueService}, but no HTTP-observable endpoint exposes queued-job identity or order
 * (only aggregate {@code succeededCount}/{@code failedCount} and printer-level diagnostics) — this
 * class can only verify the ordering of the batched {@code ItemRepository} query
 * ({@code sellerProfile.id ASC, itemNumber ASC}, Order 14 below), not the seller-level
 * {@code sellerNumber} ordering itself. Asserting that would require calling
 * {@code getSellersMatchingFilter} directly, which is an isolated service test forbidden by
 * CLAUDE.md's E2E-by-controller philosophy.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BulkSettlementReportPrintingIT extends IntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private SettlementReportRenderer settlementReportRenderer;
    @Autowired
    private PrintQueueService printQueueService;

    private static PrinterBridgeDouble printerBridgeDouble;

    private static final String EDITION_NAME = "Bourse Bilan Groupe 2026";
    private static final String ALICE_SOLD_BARCODE = "00010001"; // 5.00€, sold
    // Alice's second item (Doudou invendu, 3.00€) is never scanned — stays unsold.
    private static final String BOB_SOLD_BARCODE = "00020001"; // 10.00€, sold
    private static final String CAROL_SOLD_BARCODE = "00030001"; // 20.00€, sold

    private MockHttpSession adminSession;
    private MockHttpSession volunteer1Session;
    private Long editionId;
    private Long categoryId;
    private Long aliceId;
    private Long bobId;
    private Long carolId;
    private Long davidId;
    private Long a4PrinterId;

    @DynamicPropertySource
    static void printerBridgeProperties(DynamicPropertyRegistry registry) throws IOException {
        printerBridgeDouble = PrinterBridgeDouble.start();
        registry.add("printerbridge.base-url", printerBridgeDouble::baseUrl);
    }

    @AfterAll
    static void tearDownDouble() {
        printerBridgeDouble.stop();
    }

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
    void create_edition_and_advance_to_deposit_phase() throws Exception {
        MvcResult editionResult = mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditionDto(null, EDITION_NAME,
                                null, new BigDecimal("10.00"), Language.FR, null, false,
                                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3), null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        editionId = objectMapper.readValue(editionResult.getResponse().getContentAsString(), EditionDto.class).id();

        MvcResult categoriesResult = mockMvc.perform(put("/api/admin/editions/" + editionId + "/categories")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(new EditionCategoryDto(null, "Jouets", List.of(1))))))
                .andExpect(status().isOk())
                .andReturn();
        categoryId = objectMapper.readValue(
                categoriesResult.getResponse().getContentAsString(), new TypeReference<List<EditionCategoryDto>>() {
                }).get(0).id();

        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("DEPOSIT"));
    }

    @Test
    @Order(2)
    void create_four_sellers_alice_bob_carol_david_with_items() throws Exception {
        aliceId = createSeller("Alice", "Vendeuse", "alice.bilangroupe@email.com", "0600000001");
        bobId = createSeller("Bob", "Vendeur", "bob.bilangroupe@email.com", "0600000002");
        carolId = createSeller("Carol", "Vendeuse", "carol.bilangroupe@email.com", "0600000003");
        davidId = createSeller("David", "Vendeur", "david.bilangroupe@email.com", "0600000004");

        createItem(aliceId, "Article Alice Vendu", "5.00");
        createItem(aliceId, "Article Alice Invendu", "3.00"); // never scanned, stays unsold
        createItem(bobId, "Article Bob", "10.00");
        createItem(carolId, "Article Carol", "20.00");
        // David never deposits anything — stays UNSETTLED with zero items.
    }

    @Test
    @Order(3)
    void printing_all_reports_is_rejected_outside_the_post_sale_phase() throws Exception {
        mockMvc.perform(post("/api/admin/settlements/report/print-all")
                        .session(adminSession).with(csrf())
                        .param("filter", "ALL"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(endsWith("/settlement-not-allowed")));
    }

    @Test
    @Order(4)
    void volunteer_session_is_forbidden_on_the_admin_settlements_controller() throws Exception {
        // First test of a class-level guard for this new controller (SettlementController/
        // SettlementReportPrintService stay shared ADMIN+VOLUNTEER, story 5.1) — a plain 403,
        // independent of business state, proving @PreAuthorize("hasRole('ADMIN')") is wired.
        mockMvc.perform(post("/api/admin/settlements/report/print-all")
                        .session(volunteer1Session).with(csrf())
                        .param("filter", "ALL"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(5)
    void advance_edition_to_sale_phase() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("SALE"));
    }

    @Test
    @Order(6)
    void scan_and_validate_a_separate_sale_for_each_of_the_three_sellers() throws Exception {
        sellOneItem(ALICE_SOLD_BARCODE, "5.00");
        sellOneItem(BOB_SOLD_BARCODE, "10.00");
        sellOneItem(CAROL_SOLD_BARCODE, "20.00");
    }

    @Test
    @Order(7)
    void advance_edition_to_post_sale_phase() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("POST_SALE"));
    }

    @Test
    @Order(8)
    void settle_bob_mark_carol_unclaimed_alice_stays_unsettled() throws Exception {
        // Bob: 10.00 - 10% commission = 9.00€ due, settled in full.
        mockMvc.perform(post("/api/settlements/" + bobId + "/settle")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SettleDto(new BigDecimal("9.00")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"));

        mockMvc.perform(post("/api/settlements/" + carolId + "/unclaimed")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNCLAIMED"));
    }

    @Test
    @Order(9)
    void printing_all_reports_without_an_a4_printer_selected_returns_422() throws Exception {
        mockMvc.perform(post("/api/admin/settlements/report/print-all")
                        .session(adminSession).with(csrf())
                        .param("filter", "ALL"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(endsWith("/invalid-printer-selection")));
    }

    @Test
    @Order(10)
    void register_a4_printer_and_select_it_for_admin() throws Exception {
        printerBridgeDouble.register("bridge-bulk-report-a4", "A4 Bilan Groupe Test", "NETWORK", "ONLINE");
        MvcResult a4Result = mockMvc.perform(post("/api/admin/printers")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePrinterDto(
                                "A4 Bilan Groupe Test", PrinterType.A4, null, "bridge-bulk-report-a4"))))
                .andExpect(status().isCreated())
                .andReturn();
        a4PrinterId = objectMapper.readValue(a4Result.getResponse().getContentAsString(), PrinterDto.class).id();

        mockMvc.perform(post("/api/printers/selection")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"thermalPrinterId\":null,\"a4PrinterId\":" + a4PrinterId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.a4PrinterId").value(a4PrinterId));
    }

    @Test
    @Order(11)
    void filter_all_queues_a_report_for_every_seller() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/settlements/report/print-all")
                        .session(adminSession).with(csrf())
                        .param("filter", "ALL"))
                .andExpect(status().isOk())
                .andReturn();
        BulkSettlementReportPrintResultDto dto = objectMapper.readValue(
                result.getResponse().getContentAsString(), BulkSettlementReportPrintResultDto.class);
        assertThat(dto.succeededCount()).isEqualTo(4);
        assertThat(dto.failedCount()).isEqualTo(0);

        resetPrinterQueue();
    }

    @Test
    @Order(12)
    void filter_unsettled_queues_alice_and_davids_reports() throws Exception {
        // David has zero items — proves the itemsBySellerId.getOrDefault(..., List.of()) fallback
        // in SettlementReportPrintService.printAllReports queues successfully even for a seller
        // with no deposit.
        MvcResult result = mockMvc.perform(post("/api/admin/settlements/report/print-all")
                        .session(adminSession).with(csrf())
                        .param("filter", "UNSETTLED"))
                .andExpect(status().isOk())
                .andReturn();
        BulkSettlementReportPrintResultDto dto = objectMapper.readValue(
                result.getResponse().getContentAsString(), BulkSettlementReportPrintResultDto.class);
        assertThat(dto.succeededCount()).isEqualTo(2);
        assertThat(dto.failedCount()).isEqualTo(0);

        resetPrinterQueue();
    }

    @Test
    @Order(13)
    void filter_settled_queues_bob_and_carol_proving_it_groups_settled_and_unclaimed() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/settlements/report/print-all")
                        .session(adminSession).with(csrf())
                        .param("filter", "SETTLED"))
                .andExpect(status().isOk())
                .andReturn();
        BulkSettlementReportPrintResultDto dto = objectMapper.readValue(
                result.getResponse().getContentAsString(), BulkSettlementReportPrintResultDto.class);
        assertThat(dto.succeededCount()).isEqualTo(2);
        assertThat(dto.failedCount()).isEqualTo(0);

        resetPrinterQueue();
    }

    @Test
    @Order(14)
    @Transactional(readOnly = true)
        // read-only, no HTTP writes below: safe to keep the session open for lazy access
    void bulk_query_returns_items_grouped_by_seller_renderable_by_the_same_report_renderer() {
        List<Item> allItems = itemRepository.findAllByEditionIdForSettlementReport(editionId);
        // ORDER BY sellerProfile.id ASC (story 5.6 Task 1): the print loop groups this list by
        // seller id afterwards, so a regression dropping this clause would not necessarily break
        // any single seller's own item order, but would corrupt cross-seller grouping — verified
        // here directly since no HTTP endpoint exposes queued-job order (see class Javadoc).
        assertThat(allItems).extracting(i -> i.getSellerProfile().getId()).isSorted();

        List<Item> aliceItems = allItems.stream()
                .filter(i -> i.getSellerProfile().getId().equals(aliceId))
                .toList();
        assertThat(aliceItems).hasSize(2);
        // ORDER BY itemNumber ASC (story 5.6 Task 1): Alice's sold item was deposited before her
        // unsold one, so itemNumber ASC must return it first — a regression dropping this clause
        // would not be caught by the content-only assertions below.
        assertThat(aliceItems).extracting(Item::getName)
                .containsExactly("Article Alice Vendu", "Article Alice Invendu");

        byte[] pdf = settlementReportRenderer.renderReport(
                aliceItems.getFirst().getSellerProfile(), aliceItems, new BigDecimal("10.00"), Locale.FRENCH, null);
        String rendered = new String(pdf, StandardCharsets.ISO_8859_1);

        assertThat(rendered).startsWith("%PDF");
        assertThat(rendered).contains("Article Alice Vendu").contains("5.00");
        // Same renderer as the single-report endpoint (story 5.8 unified items table): a standalone
        // item — sold or unsold — shows on its own row with its category.
        assertThat(rendered).contains("Article Alice Invendu").contains("Jouets");
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

    private void createItem(Long sellerId, String name, String price) throws Exception {
        mockMvc.perform(post("/api/items")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateItemDto(sellerId, categoryId, name, new BigDecimal(price), false, null))))
                .andExpect(status().isCreated());
    }

    private void sellOneItem(String barcode, String price) throws Exception {
        MvcResult basketResult = mockMvc.perform(get("/api/pos/baskets/current").session(volunteer1Session))
                .andExpect(status().isOk())
                .andReturn();
        Long basketId = objectMapper.readValue(basketResult.getResponse().getContentAsString(), BasketDto.class).id();

        mockMvc.perform(post("/api/pos/baskets/" + basketId + "/items")
                        .session(volunteer1Session).with(csrf())
                        .param("barcode", barcode))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/pos/baskets/" + basketId + "/validate")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ValidateBasketDto(PaymentMethod.CASH, new BigDecimal(price)))))
                .andExpect(status().isOk());
    }

    /**
     * Between filter scenarios: the queued jobs' consumer thread genuinely attempts a WebSocket
     * connection to {@link PrinterBridgeDouble} (HTTP-only), which fails and suspends the
     * printer's queue — same real-path behavior as {@code SettlementReportPrintingIT} Order 11.
     * Discarding resets availability so the next filter scenario's up-front
     * {@code printQueueService.isAvailable} check in {@code printAllReports} passes again;
     * without it, every scenario after the first would spuriously 422.
     */
    private void resetPrinterQueue() throws Exception {
        waitUntil(() -> printQueueService.getHandle(a4PrinterId).isSuspended());
        mockMvc.perform(post("/api/admin/print-queue/" + a4PrinterId + "/discard")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());
    }

    private void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Condition not met within timeout");
    }
}
