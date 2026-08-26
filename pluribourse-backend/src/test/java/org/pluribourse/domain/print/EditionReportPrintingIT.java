package org.pluribourse.domain.print;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.ArgumentCaptor;
import org.pluribourse.domain.edition.dto.EditionCategoryDto;
import org.pluribourse.domain.edition.dto.EditionDto;
import org.pluribourse.domain.item.dto.CreateItemDto;
import org.pluribourse.domain.item.dto.CreateLotDto;
import org.pluribourse.domain.item.dto.CreateLotItemDto;
import org.pluribourse.domain.pos.dto.BasketDto;
import org.pluribourse.domain.pos.dto.SaleDto;
import org.pluribourse.domain.pos.dto.ValidateBasketDto;
import org.pluribourse.domain.pos.entity.PaymentMethod;
import org.pluribourse.domain.pos.entity.Sale;
import org.pluribourse.domain.pos.repository.SaleRepository;
import org.pluribourse.domain.print.dto.CreatePrinterDto;
import org.pluribourse.domain.print.dto.PrinterDto;
import org.pluribourse.domain.print.entity.Printer;
import org.pluribourse.domain.print.entity.PrintContentType;
import org.pluribourse.domain.print.entity.PrinterType;
import org.pluribourse.domain.print.service.DailyReportRenderer;
import org.pluribourse.domain.print.service.DepositSlipRenderer;
import org.pluribourse.domain.print.service.DocumentPrintService;
import org.pluribourse.domain.print.service.EditionReportRenderer;
import org.pluribourse.domain.print.service.InvoiceRenderer;
import org.pluribourse.domain.print.service.PrintQueueService;
import org.pluribourse.domain.print.service.PrinterBridgeClient;
import org.pluribourse.domain.print.service.SettlementReportRenderer;
import org.pluribourse.domain.report.dto.EditionSummaryReportDto;
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

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 5.4: the edition-wide summary report (FR-055, FR-094) — screen view
 * ({@code GET /admin/reports/edition/{id}}) and PDF ({@code POST /admin/reports/edition/{id}/print}),
 * admin-only, reachable in Post-vente and Clôturée alike (unlike the daily report, story 5.3, which
 * is Vente-only). Same family as {@code DailyReportPrintingIT} — AC content is verified with direct
 * calls on the real, fully-wired {@link EditionReportRenderer}/{@link DocumentPrintService} beans
 * rather than through a controller that exposes no raw PDF bytes.
 * <p>
 * Bob sells: Kapla (5.00€, CASH) and one member of a 2-item lot, Lot Duo (global price 8.00€,
 * CARD — proves a lot counts as one "sold" item even partially scanned). He also sells Livre
 * (3.00€, CHECK) — its {@code Sale.soldAt} is backdated to yesterday right after validation (no
 * HTTP mechanism can simulate "yesterday"; same targeted exception to the E2E-by-controller
 * philosophy already accepted for {@code SaleConcurrencyIT}, story 4.4) specifically to prove the
 * edition report, unlike the daily one, aggregates the edition's whole lifetime and does NOT
 * exclude it. He keeps unsold: Peluche (7.00€, category "Jouets", table 7) and Lot Duo's second,
 * never-scanned member. Edition report: 3 sold items (Kapla + Lot Duo + Livre), 2 unsold items
 * (Peluche + Lot Duo's other member), gross revenue 16.00€ (5.00 CASH + 8.00 CARD + 3.00 CHECK),
 * 10% commission = 1.60€.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EditionReportPrintingIT extends IntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private SaleRepository saleRepository;
    @Autowired
    private EditionReportRenderer editionReportRenderer;
    @Autowired
    private DailyReportRenderer dailyReportRenderer;
    @Autowired
    private DepositSlipRenderer depositSlipRenderer;
    @Autowired
    private InvoiceRenderer invoiceRenderer;
    @Autowired
    private SettlementReportRenderer settlementReportRenderer;
    @Autowired
    private PrintQueueService printQueueService;

    private static PrinterBridgeDouble printerBridgeDouble;

    private static final String EDITION_NAME = "Bourse Bilan Edition 2026";
    private static final String KAPLA_BARCODE = "00010001"; // 5.00€, sold CASH
    // Peluche (7.00€, barcode 00010002) stays unsold — its barcode is never scanned in this scenario.
    private static final String LIVRE_BARCODE = "00010003"; // 3.00€, sold CHECK, backdated to yesterday afterwards
    private static final String LOT_DUO_MEMBER_A_BARCODE = "00010004"; // Lot Duo, only member scanned, CARD
    // Lot Duo's second member (barcode 00010005) is never scanned — proves a partially-sold lot
    // still counts as exactly one sold item (decision: 1 per lot, not per member).

    private MockHttpSession adminSession;
    private MockHttpSession volunteer1Session;
    private Long editionId;
    private Long categoryId;
    private Long bobId;
    private Long a4PrinterId;
    private Long livreSaleId;

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
                        .content(objectMapper.writeValueAsString(List.of(new EditionCategoryDto(null, "Jouets", List.of(7))))))
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
    @Order(2)
    void create_seller_with_sold_and_unsold_items() throws Exception {
        MvcResult bobResult = mockMvc.perform(post("/api/sellers")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SellerDto(null, "Bob", "Vendeur", "bob.bilan@email.com", "0600000003"))))
                .andExpect(status().isCreated())
                .andReturn();
        bobId = objectMapper.readValue(bobResult.getResponse().getContentAsString(), SellerDto.class).id();

        mockMvc.perform(post("/api/items")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateItemDto(bobId, categoryId, "Kapla", new BigDecimal("5.00"), false, null))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/items")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateItemDto(bobId, categoryId, "Peluche", new BigDecimal("7.00"), false, null))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/items")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateItemDto(bobId, categoryId, "Livre", new BigDecimal("3.00"), false, null))))
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
    @Order(3)
    void register_a4_printer_and_select_it_for_admin() throws Exception {
        printerBridgeDouble.register("bridge-edition-report-a4", "A4 Bilan Test", "NETWORK", "ONLINE");
        MvcResult a4Result = mockMvc.perform(post("/api/admin/printers")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePrinterDto(
                                "A4 Bilan Test", PrinterType.A4, null, "bridge-edition-report-a4"))))
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
    @Order(4)
    void advance_edition_to_sale_phase() throws Exception {
        // 2nd advance() call since creation (1st: PREPARATION -> DEPOSIT, Order 1).
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("SALE"));
    }

    @Test
    @Order(5)
    void sell_kapla_by_cash() throws Exception {
        Long basketId = currentBasketId();
        mockMvc.perform(post("/api/pos/baskets/" + basketId + "/items")
                        .session(volunteer1Session).with(csrf())
                        .param("barcode", KAPLA_BARCODE))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/pos/baskets/" + basketId + "/validate")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ValidateBasketDto(PaymentMethod.CASH, new BigDecimal("5.00")))))
                .andExpect(status().isOk());
    }

    @Test
    @Order(6)
    void sell_one_lot_member_by_card() throws Exception {
        Long basketId = currentBasketId();
        // Only one of Lot Duo's two members is scanned — the lot stays "incomplete" at POS
        // (Story 4.3), yet must still count as exactly one sold item in the report.
        mockMvc.perform(post("/api/pos/baskets/" + basketId + "/items")
                        .session(volunteer1Session).with(csrf())
                        .param("barcode", LOT_DUO_MEMBER_A_BARCODE))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/pos/baskets/" + basketId + "/validate")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ValidateBasketDto(PaymentMethod.CARD, null))))
                .andExpect(status().isOk());
    }

    @Test
    @Order(7)
    void sell_livre_by_check_then_backdate_the_sale_to_yesterday() throws Exception {
        Long basketId = currentBasketId();
        mockMvc.perform(post("/api/pos/baskets/" + basketId + "/items")
                        .session(volunteer1Session).with(csrf())
                        .param("barcode", LIVRE_BARCODE))
                .andExpect(status().isOk());
        MvcResult result = mockMvc.perform(post("/api/pos/baskets/" + basketId + "/validate")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ValidateBasketDto(PaymentMethod.CHECK, null))))
                .andExpect(status().isOk())
                .andReturn();
        livreSaleId = objectMapper.readValue(result.getResponse().getContentAsString(), SaleDto.class).id();

        // No HTTP mechanism can simulate "yesterday" — direct repository write, exception to the
        // E2E-by-controller philosophy already accepted for SaleConcurrencyIT (story 4.4). The sale
        // itself was created through the real POS flow above; only its date is adjusted after the
        // fact, specifically to prove the edition report (unlike the daily one) does NOT exclude it.
        Sale sale = saleRepository.findById(livreSaleId).orElseThrow();
        sale.setSoldAt(LocalDateTime.now().minusDays(1));
        saleRepository.save(sale);
    }

    @Test
    @Order(8)
    void edition_report_is_rejected_during_the_sale_phase() throws Exception {
        mockMvc.perform(get("/api/admin/reports/edition/" + editionId).session(adminSession))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(endsWith("/edition-report-not-allowed")));
        mockMvc.perform(post("/api/admin/reports/edition/" + editionId + "/print")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(endsWith("/edition-report-not-allowed")));
    }

    @Test
    @Order(9)
    void advance_edition_to_post_sale_phase() throws Exception {
        // 3rd advance() call since creation: SALE -> POST_SALE.
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("POST_SALE"));
    }

    @Test
    @Order(10)
    void edition_report_reflects_the_whole_edition_lifetime_lot_aware_and_unsold_snapshot() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/admin/reports/edition/" + editionId).session(adminSession))
                .andExpect(status().isOk())
                .andReturn();
        EditionSummaryReportDto report = objectMapper.readValue(result.getResponse().getContentAsString(), EditionSummaryReportDto.class);

        assertEditionReport(report);
    }

    @Test
    @Order(11)
    void advancing_to_closed_leaves_the_edition_report_unchanged_and_still_resolvable_by_id() throws Exception {
        // POST_SALE -> CLOSED via the dedicated /close endpoint (FR-096 follow-up fix). Proves
        // resolution by explicit edition ID (EditionService.requireEdition, not getActiveEdition())
        // keeps this endpoint correct in Clôturée — the central point of story 5.4's endpoint design.
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/close")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("CLOSED"));

        MvcResult result = mockMvc.perform(get("/api/admin/reports/edition/" + editionId).session(adminSession))
                .andExpect(status().isOk())
                .andReturn();
        EditionSummaryReportDto report = objectMapper.readValue(result.getResponse().getContentAsString(), EditionSummaryReportDto.class);

        // Nothing changes between Post-vente and Clôturée — no sale is possible in the interval.
        assertEditionReport(report);
    }

    @Test
    @Order(12)
    void volunteer_cannot_access_the_edition_report() throws Exception {
        mockMvc.perform(get("/api/admin/reports/edition/" + editionId).session(volunteer1Session))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/reports/edition/" + editionId + "/print")
                        .session(volunteer1Session).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(13)
    void edition_report_for_an_unknown_edition_id_returns_404() throws Exception {
        mockMvc.perform(get("/api/admin/reports/edition/999999").session(adminSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(endsWith("/edition-not-found")));
    }

    @Test
    @Order(14)
    void edition_report_renderer_includes_counts_totals_and_payment_breakdown() {
        EditionSummaryReportDto report = new EditionSummaryReportDto(3, 2,
                new BigDecimal("16.00"), new BigDecimal("1.60"),
                new BigDecimal("5.00"), new BigDecimal("3.00"), new BigDecimal("8.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, "€");

        byte[] pdf = editionReportRenderer.renderEditionReport(EDITION_NAME, report, Locale.FRENCH);
        String rendered = new String(pdf, StandardCharsets.ISO_8859_1);

        assertThat(rendered).startsWith("%PDF");
        assertThat(rendered).contains(EDITION_NAME);
        assertThat(rendered).contains("16.00").contains("1.60");
        assertThat(rendered).contains("Espèces").contains("Chèque").contains("Carte");
        assertThat(rendered).contains("5.00").contains("3.00").contains("8.00");
    }

    @Test
    @Order(15)
    void edition_report_renderer_resolves_labels_from_the_edition_document_language_not_user_preference() {
        // AC 1: document language is resolved from Edition.documentLanguage — here forced to
        // Locale.ENGLISH directly (same real MessageSource-backed renderer bean) to prove
        // messages_en.properties is actually picked up, independently of the connected user's own
        // language preference — same reasoning as DailyReportPrintingIT Order 12.
        EditionSummaryReportDto report = new EditionSummaryReportDto(3, 2,
                new BigDecimal("16.00"), new BigDecimal("1.60"),
                new BigDecimal("5.00"), new BigDecimal("3.00"), new BigDecimal("8.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, "€");

        byte[] pdf = editionReportRenderer.renderEditionReport(EDITION_NAME, report, Locale.ENGLISH);
        String rendered = new String(pdf, StandardCharsets.ISO_8859_1);

        assertThat(rendered).contains("Edition summary").contains("Payment method breakdown").contains("Cash").contains("Check").contains("Card");
        assertThat(rendered).doesNotContain("Bilan d'édition").doesNotContain("Espèces").doesNotContain("Chèque");
    }

    @Test
    @Order(16)
    void document_print_service_sends_the_rendered_edition_report_pdf_bytes_via_printer_bridge_client() {
        EditionSummaryReportDto report = new EditionSummaryReportDto(3, 2,
                new BigDecimal("16.00"), new BigDecimal("1.60"),
                new BigDecimal("5.00"), new BigDecimal("3.00"), new BigDecimal("8.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, "€");

        PrinterBridgeClient mockClient = mock(PrinterBridgeClient.class);
        DocumentPrintService isolatedDocumentPrintService = new DocumentPrintService(
                depositSlipRenderer, invoiceRenderer, settlementReportRenderer, dailyReportRenderer, editionReportRenderer, mockClient);

        Printer printer = new Printer();
        printer.setPrinterBridgeId("bridge-edition-report-mock-target");
        isolatedDocumentPrintService.buildEditionReportJob(EDITION_NAME, report, Locale.FRENCH).execute(printer);

        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(mockClient).print(eq("bridge-edition-report-mock-target"), eq(PrintContentType.PDF), payloadCaptor.capture());
        assertThat(new String(payloadCaptor.getValue(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    @Test
    @Order(17)
    void printing_the_edition_report_via_http_is_queued_and_reaches_printer_bridge_client() throws Exception {
        mockMvc.perform(post("/api/admin/reports/edition/" + editionId + "/print")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());

        // A 204 only proves the job was queued, not that it actually executed — the PDF render
        // happens later, on the queue's own consumer thread. This uses the REAL, Spring-wired
        // PrinterBridgeClient (not mocked, unlike Order 16) — so the job genuinely attempts a
        // WebSocket connection to PrinterBridgeDouble, which is HTTP-only and cannot complete the
        // handshake. The job therefore fails and suspends this printer's queue — expected here,
        // proving the HTTP-triggered production path (controller -> service -> real
        // PrinterBridgeClient) runs end to end without throwing before reaching PrinterBridgeClient.
        waitUntil(() -> printQueueService.getHandle(a4PrinterId).isSuspended());
        assertThat(printQueueService.getHandle(a4PrinterId).getLastError()).isNotNull();
    }

    @Test
    @Order(18)
    void printing_without_an_a4_printer_selected_returns_422() throws Exception {
        mockMvc.perform(post("/api/admin/print-queue/" + a4PrinterId + "/discard")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/printers/selection")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"thermalPrinterId\":null,\"a4PrinterId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.a4PrinterId").isEmpty());

        mockMvc.perform(post("/api/admin/reports/edition/" + editionId + "/print")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(endsWith("/invalid-printer-selection")));
    }

    @Test
    @Order(19)
    void edition_report_renderer_uses_the_passed_currency_not_a_hardcoded_symbol() {
        EditionSummaryReportDto report = new EditionSummaryReportDto(3, 2,
                new BigDecimal("16.00"), new BigDecimal("1.60"),
                new BigDecimal("5.00"), new BigDecimal("3.00"), new BigDecimal("8.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, "$");

        byte[] pdf = editionReportRenderer.renderEditionReport(EDITION_NAME, report, Locale.FRENCH);
        String rendered = new String(pdf, StandardCharsets.ISO_8859_1);

        // "$" is plain ASCII (unlike "€", never assertable as a literal character here) — its
        // presence proves the passed currency reached the rendered text, not a symbol baked into
        // the template.
        assertThat(rendered).contains("16.00$").contains("1.60$");
    }

    private void assertEditionReport(EditionSummaryReportDto report) {
        // Kapla (CASH) + Lot Duo (CARD, one line despite two members) + Livre (CHECK, backdated to
        // yesterday) = 3 — unlike the daily report, the edition report is NOT bounded to today.
        assertThat(report.soldItemCount()).isEqualTo(3);
        // Peluche + Lot Duo's second, never-scanned member = 2.
        assertThat(report.unsoldItemCount()).isEqualTo(2);
        assertThat(report.grossRevenue()).isEqualByComparingTo("16.00");
        assertThat(report.commission()).isEqualByComparingTo("1.60");
        assertThat(report.cashTotal()).isEqualByComparingTo("5.00");
        assertThat(report.cardTotal()).isEqualByComparingTo("8.00");
        assertThat(report.checkTotal()).isEqualByComparingTo("3.00");
    }

    private Long currentBasketId() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/pos/baskets/current").session(volunteer1Session))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), BasketDto.class).id();
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
