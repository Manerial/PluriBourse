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
import org.pluribourse.domain.item.entity.Item;
import org.pluribourse.domain.item.repository.ItemRepository;
import org.pluribourse.domain.payout.dto.SettlementDto;
import org.pluribourse.domain.pos.dto.BasketDto;
import org.pluribourse.domain.pos.dto.ValidateBasketDto;
import org.pluribourse.domain.pos.entity.PaymentMethod;
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
 * Story 5.2: printing the per-seller sales report PDF ("bilan de vente", FR-050). Distinct business
 * scenario from {@code SettlementIT} (settle/unclaimed CRUD, story 5.1) — printer registration/
 * selection, {@link PrinterBridgeDouble}, direct calls on the real renderer — same reasoning as
 * {@code InvoicePrintingIT} being separate from {@code PosBasketIT} (see that class's Javadoc for
 * why AC content is verified with direct calls on the real, fully-wired
 * {@link SettlementReportRenderer}/{@link DocumentPrintService} beans rather than through a
 * controller that exposes no raw PDF bytes). Alice sells: a standalone item (Kapla, 5.00€), an
 * "incomplete" standalone item (Doudou, 2.00€, {@code incomplete=true} — proves AC 2, no discount
 * on commission or price) and one member of a 2-item lot (Lot Mixte, global price 8.00€ — proves a
 * partially-sold lot rolls up as a single "sold" line, code review patch 2026-08-14). She keeps
 * unsold: a standalone item (Peluche, 7.00€, category "Jouets", table 7) and an entirely untouched
 * 2-item lot (Lot Invendu, global price 6.00€ — proves a lot shows its price in the unsold section
 * too, unlike a standalone unsold item). Gross total = 5.00+2.00+8.00 = 15.00€, 10% commission,
 * net due = 13.50€.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SettlementReportPrintingIT extends IntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private SettlementReportRenderer settlementReportRenderer;
    @Autowired
    private DepositSlipRenderer depositSlipRenderer;
    @Autowired
    private InvoiceRenderer invoiceRenderer;
    @Autowired
    private DailyReportRenderer dailyReportRenderer;
    @Autowired
    private EditionReportRenderer editionReportRenderer;
    @Autowired
    private PrintQueueService printQueueService;

    private static PrinterBridgeDouble printerBridgeDouble;

    private static final String EDITION_NAME = "Bourse Bilan 2026";
    private static final String KAPLA_BARCODE = "00010001"; // 5.00€, sold
    // Peluche (7.00€) stays unsold — its barcode is never scanned in this scenario.
    private static final String DOUDOU_INCOMPLET_BARCODE = "00010003"; // 2.00€, incomplete=true, sold (AC 2)
    private static final String MIXED_LOT_ITEM_A_BARCODE = "00010004"; // Lot Mixte, only member scanned
    // Lot Mixte's second member (barcode 00010005) is never scanned — proves a partially-sold lot
    // still rolls up as a single "sold" line, full price counted once (code review patch).
    private static final String MIXED_LOT_NAME = "Lot Mixte";
    // Lot Invendu (barcodes 00010006/00010007) is never scanned at all — proves a fully-unsold lot
    // shows its price in the "Articles invendus" section too, unlike a standalone unsold item.
    private static final String UNSOLD_LOT_NAME = "Lot Invendu";

    private MockHttpSession adminSession;
    private MockHttpSession volunteer1Session;
    private Long editionId;
    private Long categoryId;
    private Long aliceId;
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
                        .content(objectMapper.writeValueAsString(List.of(new EditionCategoryDto(null, "Jouets", List.of(7))))))
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
    void create_seller_with_a_sold_and_an_unsold_item() throws Exception {
        MvcResult aliceResult = mockMvc.perform(post("/api/sellers")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SellerDto(null, "Alice", "Vendeuse", "alice.bilan@email.com", "0600000001"))))
                .andExpect(status().isCreated())
                .andReturn();
        aliceId = objectMapper.readValue(aliceResult.getResponse().getContentAsString(), SellerDto.class).id();

        mockMvc.perform(post("/api/items")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateItemDto(aliceId, categoryId, "Kapla", new BigDecimal("5.00"), false, null))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/items")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateItemDto(aliceId, categoryId, "Peluche", new BigDecimal("7.00"), false, null))))
                .andExpect(status().isCreated());
        // AC 2: the "incomplete" flag never affects the price or the commission applied to it.
        mockMvc.perform(post("/api/items")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateItemDto(aliceId, categoryId, "Doudou", new BigDecimal("2.00"), true, null))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/lots")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateLotDto(aliceId, MIXED_LOT_NAME, new BigDecimal("8.00"),
                                List.of(new CreateLotItemDto(categoryId, "Mixte A", false, null),
                                        new CreateLotItemDto(categoryId, "Mixte B", false, null))))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/lots")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateLotDto(aliceId, UNSOLD_LOT_NAME, new BigDecimal("6.00"),
                                List.of(new CreateLotItemDto(categoryId, "Invendu A", false, null),
                                        new CreateLotItemDto(categoryId, "Invendu B", false, null))))))
                .andExpect(status().isCreated());
    }

    @Test
    @Order(3)
    void printing_report_is_rejected_outside_the_post_sale_phase() throws Exception {
        mockMvc.perform(post("/api/settlements/" + aliceId + "/report/print")
                        .session(volunteer1Session).with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(endsWith("/settlement-not-allowed")));
    }

    @Test
    @Order(4)
    void register_a4_printer_and_select_it_for_volunteer_and_admin() throws Exception {
        printerBridgeDouble.register("bridge-report-a4", "A4 Bilan Test", "NETWORK", "ONLINE");
        MvcResult a4Result = mockMvc.perform(post("/api/admin/printers")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePrinterDto(
                                "A4 Bilan Test", PrinterType.A4, null, "bridge-report-a4"))))
                .andExpect(status().isCreated())
                .andReturn();
        a4PrinterId = objectMapper.readValue(a4Result.getResponse().getContentAsString(), PrinterDto.class).id();

        mockMvc.perform(post("/api/printers/selection")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"thermalPrinterId\":null,\"a4PrinterId\":" + a4PrinterId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.a4PrinterId").value(a4PrinterId));

        // AC5: an admin session must also be able to select an A4 printer — proves the frontend
        // change (opening /printer-selection to the admin) has a working backend counterpart.
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
    void scan_and_validate_a_sale_for_kapla_the_incomplete_item_and_one_lot_member() throws Exception {
        MvcResult basketResult = mockMvc.perform(get("/api/pos/baskets/current").session(volunteer1Session))
                .andExpect(status().isOk())
                .andReturn();
        Long basketId = objectMapper.readValue(basketResult.getResponse().getContentAsString(), BasketDto.class).id();

        mockMvc.perform(post("/api/pos/baskets/" + basketId + "/items")
                        .session(volunteer1Session).with(csrf())
                        .param("barcode", KAPLA_BARCODE))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/pos/baskets/" + basketId + "/items")
                        .session(volunteer1Session).with(csrf())
                        .param("barcode", DOUDOU_INCOMPLET_BARCODE))
                .andExpect(status().isOk());
        // Only one of Lot Mixte's two members is scanned — the lot stays "incomplete" at POS
        // (Story 4.3), yet must still roll up as a single sold line in the report (AC 1).
        mockMvc.perform(post("/api/pos/baskets/" + basketId + "/items")
                        .session(volunteer1Session).with(csrf())
                        .param("barcode", MIXED_LOT_ITEM_A_BARCODE))
                .andExpect(status().isOk());

        // Total = 5.00 (Kapla) + 2.00 (Doudou) + 8.00 (Lot Mixte, full lot price for one scanned member) = 15.00
        mockMvc.perform(post("/api/pos/baskets/" + basketId + "/validate")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ValidateBasketDto(PaymentMethod.CASH, new BigDecimal("15.00")))))
                .andExpect(status().isOk());
    }

    @Test
    @Order(7)
    void advance_edition_to_post_sale_phase() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("POST_SALE"));

        // Sanity check reusing the story 5.1 endpoint: amount due is 15.00 - 10% = 13.50€.
        MvcResult result = mockMvc.perform(get("/api/settlements").session(volunteer1Session))
                .andExpect(status().isOk())
                .andReturn();
        List<SettlementDto> settlements = objectMapper.readValue(
                result.getResponse().getContentAsString(), new TypeReference<List<SettlementDto>>() {
                });
        assertThat(settlements.stream().filter(s -> s.sellerId().equals(aliceId)).findFirst().orElseThrow().amountDue())
                .isEqualByComparingTo("13.50");
    }

    @Test
    @Order(8)
    @Transactional(readOnly = true)
        // read-only, no HTTP writes below: safe to keep the session open for lazy access
    void settlement_report_renderer_includes_sold_and_unsold_sections_with_totals() {
        List<Item> items = itemRepository.findAllBySellerProfileIdForSettlementReport(aliceId);

        byte[] pdf = settlementReportRenderer.renderReport(items.getFirst().getSellerProfile(), items, new BigDecimal("10.00"), Locale.FRENCH, null);
        String rendered = new String(pdf, StandardCharsets.ISO_8859_1);

        assertThat(rendered).startsWith("%PDF");
        // Sold section: Kapla (5.00€) and the incomplete Doudou (2.00€, AC 2 — full price, no
        // commission exemption despite incomplete=true).
        assertThat(rendered).contains("Kapla").contains("5.00");
        assertThat(rendered).contains("Doudou").contains("2.00");
        // Sold section: Lot Mixte appears on exactly ONE line even though only one of its two
        // members was scanned — its full price counted once, never split or duplicated into the
        // unsold section below (code review patch, 2026-08-14).
        assertThat(countOccurrences(rendered, MIXED_LOT_NAME)).isEqualTo(1);
        assertThat(countOccurrences(rendered, "8.00")).isEqualTo(1);
        // Unsold section: Peluche, its category and table number (FR-050) — no price cell for a
        // standalone unsold item.
        assertThat(rendered).contains("Peluche").contains("Jouets").contains("7");
        // Unsold section: Lot Invendu (never scanned) shows its name, category, table AND its
        // price — unlike a standalone unsold item, a lot must show its price regardless of section
        // (AC 1).
        assertThat(countOccurrences(rendered, UNSOLD_LOT_NAME)).isEqualTo(1);
        assertThat(rendered).contains("6.00");
        // Total brut (5.00+2.00+8.00=15.00), commission (10%) and net (13.50) — all BigDecimal,
        // precise to the cent.
        assertThat(rendered).contains("15.00").contains("13.50");
        // Commission amount (10% of 15.00 = 1.50€, distinct from the already-asserted 10% rate
        // line) is now printed alongside the rate.
        assertThat(rendered).contains("1.50");
        // Alice has not been settled yet at this point in the scenario (Order 7 only reads
        // amountDue) — no "montant remis" line should appear.
        assertThat(rendered).doesNotContain("Montant remis");
    }

    @Test
    @Order(9)
    @Transactional(readOnly = true)
        // read-only, no HTTP writes below: safe to keep the session open for lazy access
    void settlement_report_renderer_resolves_labels_from_the_edition_document_language_not_user_preference() {
        // AC 3: document language is resolved from Edition.documentLanguage — here forced to
        // Locale.ENGLISH directly (same items, same real MessageSource-backed renderer bean) to
        // prove messages_en.properties is actually picked up, independently of the connected
        // user's own language preference. SettlementReportPrintService.printReport() performs the
        // exact same resolution (edition.getDocumentLanguage() == FR ? FRENCH : ENGLISH) — its
        // ternary itself is trivial; what actually needed proof is that the renderer's message
        // resolution genuinely switches language, which this exercises directly.
        List<Item> items = itemRepository.findAllBySellerProfileIdForSettlementReport(aliceId);

        byte[] pdf = settlementReportRenderer.renderReport(items.getFirst().getSellerProfile(), items, new BigDecimal("10.00"), Locale.ENGLISH, null);
        String rendered = new String(pdf, StandardCharsets.ISO_8859_1);

        assertThat(rendered).contains("Sales report").contains("Sold items").contains("Unsold items");
        assertThat(rendered).doesNotContain("Bilan de vente").doesNotContain("Articles vendus").doesNotContain("Articles invendus");
    }

    @Test
    @Order(10)
    @Transactional(readOnly = true)
        // Same eager-loading constraint as production: execute() below runs synchronously in this
        // test thread, but the pattern (touching lazy associations before execute()) mirrors the
        // real queue consumer thread's requirement.
    void document_print_service_sends_the_rendered_report_pdf_bytes_via_printer_bridge_client() {
        List<Item> items = itemRepository.findAllBySellerProfileIdForSettlementReport(aliceId);

        PrinterBridgeClient mockClient = mock(PrinterBridgeClient.class);
        DocumentPrintService isolatedDocumentPrintService =
                new DocumentPrintService(depositSlipRenderer, invoiceRenderer, settlementReportRenderer, dailyReportRenderer,
                        editionReportRenderer, mockClient);

        Printer printer = new Printer();
        printer.setPrinterBridgeId("bridge-report-mock-target");
        isolatedDocumentPrintService.buildSettlementReportJob(
                items.getFirst().getSellerProfile(), items, new BigDecimal("10.00"), Locale.FRENCH, null).execute(printer);

        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(mockClient).print(eq("bridge-report-mock-target"), eq(PrintContentType.PDF), payloadCaptor.capture());
        assertThat(new String(payloadCaptor.getValue(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    @Test
    @Order(11)
    void printing_the_report_via_http_is_queued_reaches_printer_bridge_client_and_stays_reprintable() throws Exception {
        mockMvc.perform(post("/api/settlements/" + aliceId + "/report/print")
                        .session(volunteer1Session).with(csrf()))
                .andExpect(status().isNoContent());

        // A 204 only proves the job was queued, not that it actually executed — the PDF render
        // happens later, on the queue's own consumer thread. This uses the REAL, Spring-wired
        // PrinterBridgeClient (not mocked, unlike Order 10) — so the report job genuinely attempts a
        // WebSocket connection to PrinterBridgeDouble, which is HTTP-only and cannot complete the
        // WS handshake. The job therefore fails and suspends this printer's queue — expected here,
        // proving the HTTP-triggered production path (controller -> service -> real
        // PrinterBridgeClient) runs end to end without throwing before reaching PrinterBridgeClient.
        waitUntil(() -> printQueueService.getHandle(a4PrinterId).isSuspended());
        assertThat(printQueueService.getHandle(a4PrinterId).getLastError()).isNotNull();

        // Reprintable: no "already printed" state anywhere in SettlementReportPrintService — a
        // second print request for the same seller must still be accepted.
        mockMvc.perform(post("/api/admin/print-queue/" + a4PrinterId + "/discard")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/settlements/" + aliceId + "/report/print")
                        .session(volunteer1Session).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(12)
    void printing_the_report_is_also_reachable_by_an_admin_session() throws Exception {
        waitUntil(() -> printQueueService.getHandle(a4PrinterId).isSuspended());
        mockMvc.perform(post("/api/admin/print-queue/" + a4PrinterId + "/discard")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/settlements/" + aliceId + "/report/print")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(13)
    void printing_without_an_a4_printer_selected_returns_422() throws Exception {
        mockMvc.perform(post("/api/printers/selection")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"thermalPrinterId\":null,\"a4PrinterId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.a4PrinterId").isEmpty());

        mockMvc.perform(post("/api/settlements/" + aliceId + "/report/print")
                        .session(volunteer1Session).with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(endsWith("/invalid-printer-selection")));
    }

    /**
     * IDOR proof (requireSellerOfEdition, reused from SettlementService): closes edition 1 (only
     * one edition can be active at a time) so a second edition can become active, then requests
     * Alice's report — who still belongs to edition 1, now CLOSED — against it. The generic 404
     * (not 422/403) is the actual proof: indistinguishable from a seller that doesn't exist at all.
     */
    @Test
    @Order(14)
    void printing_a_sellers_report_from_a_different_edition_returns_404() throws Exception {
        // POST_SALE → CLOSED only via the dedicated /close endpoint (FR-096 follow-up fix).
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/close")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("CLOSED"));

        MvcResult edition2Result = mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditionDto(null, "Bourse Bilan 2027", null, null, null, null, false, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 3), null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        Long edition2Id = objectMapper.readValue(edition2Result.getResponse().getContentAsString(), EditionDto.class).id();

        mockMvc.perform(put("/api/admin/editions/" + edition2Id + "/categories")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(new EditionCategoryDto(null, "Jouets", List.of(1))))))
                .andExpect(status().isOk());

        for (String expectedPhase : List.of("DEPOSIT", "SALE", "POST_SALE")) {
            mockMvc.perform(post("/api/admin/editions/" + edition2Id + "/phase/advance")
                            .session(adminSession).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.phase").value(expectedPhase));
        }

        mockMvc.perform(post("/api/settlements/" + aliceId + "/report/print")
                        .session(volunteer1Session).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(endsWith("/seller-not-found")));
    }

    @Test
    @Order(15)
    @Transactional(readOnly = true)
        // read-only, no HTTP writes below: mutates the in-memory Edition only (never flushed),
        // same reasoning as DepositSlipPrintingIT's Order(15) currency test.
    void settlement_report_renderer_uses_the_edition_currency_not_a_hardcoded_symbol() {
        List<Item> items = itemRepository.findAllBySellerProfileIdForSettlementReport(aliceId);
        items.getFirst().getSellerProfile().getEdition().setCurrency("$");

        byte[] pdf = settlementReportRenderer.renderReport(items.getFirst().getSellerProfile(), items, new BigDecimal("10.00"), Locale.FRENCH, null);
        String rendered = new String(pdf, StandardCharsets.ISO_8859_1);

        // "$" is plain ASCII (unlike "€", never assertable as a literal character here) — its
        // presence proves the renderer used the edition's currency, not a symbol baked into the
        // template.
        assertThat(rendered).contains("15.00$").contains("13.50$");
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

    private long countOccurrences(String haystack, String needle) {
        long count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) != -1) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
