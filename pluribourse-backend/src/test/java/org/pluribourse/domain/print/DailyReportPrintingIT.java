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
import org.pluribourse.domain.report.dto.DailySalesReportDto;
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
 * Story 5.3: the daily sales report (FR-054, FR-094) — screen view ({@code GET /admin/reports/daily})
 * and PDF ({@code POST /admin/reports/daily/print}), admin-only, phase-Vente-only. Same family as
 * {@code SettlementReportPrintingIT}/{@code InvoicePrintingIT} (see their Javadoc for why AC content
 * is verified with direct calls on the real, fully-wired {@link DailyReportRenderer}/
 * {@link DocumentPrintService} beans rather than through a controller that exposes no raw PDF
 * bytes). Bob sells today: Kapla (5.00€, CASH) and one member of a 2-item lot, Lot Duo (global price
 * 8.00€, CARD — proves a lot counts as one "sold" item even partially scanned). He also sells Livre
 * (3.00€, CHECK) — its {@code Sale.soldAt} is backdated to yesterday right after validation (no HTTP
 * mechanism can simulate "yesterday"; same targeted exception to the E2E-by-controller philosophy
 * already accepted for {@code SaleConcurrencyIT}, story 4.4) to prove the calendar-day boundary
 * excludes it from today's report. He keeps unsold: Peluche (7.00€, category "Jouets", table 7) and
 * Lot Duo's second, never-scanned member. Today's report: 2 sold items (Kapla + Lot Duo), 2 unsold
 * items (Peluche + Lot Duo's other member), gross revenue 13.00€ (5.00 CASH + 8.00 CARD, Livre's
 * 3.00 CHECK excluded), 10% commission = 1.30€.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DailyReportPrintingIT extends IntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private SaleRepository saleRepository;
    @Autowired
    private DailyReportRenderer dailyReportRenderer;
    @Autowired
    private EditionReportRenderer editionReportRenderer;
    @Autowired
    private DepositSlipRenderer depositSlipRenderer;
    @Autowired
    private InvoiceRenderer invoiceRenderer;
    @Autowired
    private SettlementReportRenderer settlementReportRenderer;
    @Autowired
    private PrintQueueService printQueueService;

    private static PrinterBridgeDouble printerBridgeDouble;

    private static final String EDITION_NAME = "Bourse Rapport 2026";
    private static final String KAPLA_BARCODE = "00010001"; // 5.00€, sold today CASH
    // Peluche (7.00€, barcode 00010002) stays unsold — its barcode is never scanned in this scenario.
    private static final String LIVRE_BARCODE = "00010003"; // 3.00€, sold today CHECK, backdated to yesterday afterwards
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
                        .content(objectMapper.writeValueAsString(new SellerDto(null, "Bob", "Vendeur", "bob.rapport@email.com", "0600000002"))))
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
                        .content(objectMapper.writeValueAsString(new CreateLotDto(bobId, "Lot Duo", new BigDecimal("8.00"),
                                List.of(new CreateLotItemDto(categoryId, "Duo A", false, null),
                                        new CreateLotItemDto(categoryId, "Duo B", false, null))))))
                .andExpect(status().isCreated());
    }

    @Test
    @Order(3)
    void daily_report_is_rejected_outside_the_sale_phase() throws Exception {
        mockMvc.perform(get("/api/admin/reports/daily").session(adminSession))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(endsWith("/sale-phase-required")));
        mockMvc.perform(post("/api/admin/reports/daily/print")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(endsWith("/sale-phase-required")));
    }

    @Test
    @Order(4)
    void register_a4_printer_and_select_it_for_admin() throws Exception {
        printerBridgeDouble.register("bridge-daily-report-a4", "A4 Rapport Test", "NETWORK", "ONLINE");
        MvcResult a4Result = mockMvc.perform(post("/api/admin/printers")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePrinterDto(
                                "A4 Rapport Test", PrinterType.A4, null, "bridge-daily-report-a4"))))
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
    @Order(5)
    void advance_edition_to_sale_phase() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("SALE"));
    }

    @Test
    @Order(6)
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
    @Order(7)
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
    @Order(8)
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
        // fact. findById()/save() are each independently transactional via Spring Data's own
        // repository proxy — no wrapping @Transactional needed on this helper.
        Sale sale = saleRepository.findById(livreSaleId).orElseThrow();
        sale.setSoldAt(LocalDateTime.now().minusDays(1));
        saleRepository.save(sale);
    }

    @Test
    @Order(9)
    void daily_report_reflects_only_todays_sales_lot_aware_and_unsold_snapshot() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/admin/reports/daily").session(adminSession))
                .andExpect(status().isOk())
                .andReturn();
        DailySalesReportDto report = objectMapper.readValue(result.getResponse().getContentAsString(), DailySalesReportDto.class);

        assertThat(report.reportDate()).isEqualTo(LocalDate.now());
        // Kapla (CASH) + Lot Duo (CARD, one line despite two members) = 2. Livre (backdated) excluded.
        assertThat(report.soldItemCount()).isEqualTo(2);
        // Peluche + Lot Duo's second, never-scanned member = 2 — independent of the calendar day.
        assertThat(report.unsoldItemCount()).isEqualTo(2);
        assertThat(report.grossRevenue()).isEqualByComparingTo("13.00");
        assertThat(report.commission()).isEqualByComparingTo("1.30");
        assertThat(report.cashTotal()).isEqualByComparingTo("5.00");
        assertThat(report.cardTotal()).isEqualByComparingTo("8.00");
        // Livre's 3.00€ CHECK sale was backdated to yesterday — excluded from today's breakdown.
        assertThat(report.checkTotal()).isEqualByComparingTo("0.00");
    }

    @Test
    @Order(10)
    void volunteer_cannot_access_the_daily_report() throws Exception {
        mockMvc.perform(get("/api/admin/reports/daily").session(volunteer1Session))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/reports/daily/print")
                        .session(volunteer1Session).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(11)
    void daily_report_renderer_includes_counts_totals_and_payment_breakdown() {
        DailySalesReportDto report = new DailySalesReportDto(LocalDate.of(2026, 1, 2), 2, 2,
                new BigDecimal("13.00"), new BigDecimal("1.30"),
                new BigDecimal("5.00"), new BigDecimal("0.00"), new BigDecimal("8.00"), "€");

        byte[] pdf = dailyReportRenderer.renderDailyReport(EDITION_NAME, report, Locale.FRENCH);
        String rendered = new String(pdf, StandardCharsets.ISO_8859_1);

        assertThat(rendered).startsWith("%PDF");
        assertThat(rendered).contains(EDITION_NAME);
        assertThat(rendered).contains("2026-01-02");
        assertThat(rendered).contains("13.00").contains("1.30");
        assertThat(rendered).contains("Espèces").contains("Chèque").contains("Carte");
        assertThat(rendered).contains("5.00").contains("8.00");
    }

    @Test
    @Order(12)
    void daily_report_renderer_resolves_labels_from_the_edition_document_language_not_user_preference() {
        // AC 2: document language is resolved from Edition.documentLanguage — here forced to
        // Locale.ENGLISH directly (same real MessageSource-backed renderer bean) to prove
        // messages_en.properties is actually picked up, independently of the connected user's own
        // language preference — same reasoning as SettlementReportPrintingIT Order 9.
        DailySalesReportDto report = new DailySalesReportDto(LocalDate.of(2026, 1, 2), 2, 2,
                new BigDecimal("13.00"), new BigDecimal("1.30"),
                new BigDecimal("5.00"), new BigDecimal("0.00"), new BigDecimal("8.00"), "€");

        byte[] pdf = dailyReportRenderer.renderDailyReport(EDITION_NAME, report, Locale.ENGLISH);
        String rendered = new String(pdf, StandardCharsets.ISO_8859_1);

        assertThat(rendered).contains("Daily sales report").contains("Payment method breakdown").contains("Cash").contains("Check").contains("Card");
        assertThat(rendered).doesNotContain("Bilan des ventes journalier").doesNotContain("Espèces").doesNotContain("Chèque");
    }

    @Test
    @Order(13)
    void document_print_service_sends_the_rendered_daily_report_pdf_bytes_via_printer_bridge_client() {
        DailySalesReportDto report = new DailySalesReportDto(LocalDate.of(2026, 1, 2), 2, 2,
                new BigDecimal("13.00"), new BigDecimal("1.30"),
                new BigDecimal("5.00"), new BigDecimal("0.00"), new BigDecimal("8.00"), "€");

        PrinterBridgeClient mockClient = mock(PrinterBridgeClient.class);
        DocumentPrintService isolatedDocumentPrintService =
                new DocumentPrintService(depositSlipRenderer, invoiceRenderer, settlementReportRenderer, dailyReportRenderer,
                        editionReportRenderer, mockClient);

        Printer printer = new Printer();
        printer.setPrinterBridgeId("bridge-daily-report-mock-target");
        isolatedDocumentPrintService.buildDailyReportJob(EDITION_NAME, report, Locale.FRENCH).execute(printer);

        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(mockClient).print(eq("bridge-daily-report-mock-target"), eq(PrintContentType.PDF), payloadCaptor.capture());
        assertThat(new String(payloadCaptor.getValue(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    @Test
    @Order(14)
    void printing_the_daily_report_via_http_is_queued_and_reaches_printer_bridge_client() throws Exception {
        mockMvc.perform(post("/api/admin/reports/daily/print")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());

        // A 204 only proves the job was queued, not that it actually executed — the PDF render
        // happens later, on the queue's own consumer thread. This uses the REAL, Spring-wired
        // PrinterBridgeClient (not mocked, unlike Order 13) — so the job genuinely attempts a
        // WebSocket connection to PrinterBridgeDouble, which is HTTP-only and cannot complete the
        // handshake. The job therefore fails and suspends this printer's queue — expected here,
        // proving the HTTP-triggered production path (controller -> service -> real
        // PrinterBridgeClient) runs end to end without throwing before reaching PrinterBridgeClient.
        waitUntil(() -> printQueueService.getHandle(a4PrinterId).isSuspended());
        assertThat(printQueueService.getHandle(a4PrinterId).getLastError()).isNotNull();
    }

    @Test
    @Order(15)
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

        mockMvc.perform(post("/api/admin/reports/daily/print")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(endsWith("/invalid-printer-selection")));
    }

    @Test
    @Order(16)
    void daily_report_renderer_uses_the_passed_currency_not_a_hardcoded_symbol() {
        DailySalesReportDto report = new DailySalesReportDto(LocalDate.of(2026, 1, 2), 2, 2,
                new BigDecimal("13.00"), new BigDecimal("1.30"),
                new BigDecimal("5.00"), new BigDecimal("0.00"), new BigDecimal("8.00"), "$");

        byte[] pdf = dailyReportRenderer.renderDailyReport(EDITION_NAME, report, Locale.FRENCH);
        String rendered = new String(pdf, StandardCharsets.ISO_8859_1);

        // "$" is plain ASCII (unlike "€", never assertable as a literal character here) — its
        // presence proves the passed currency reached the rendered text, not a symbol baked into
        // the template.
        assertThat(rendered).contains("13.00$").contains("1.30$");
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
