package org.pluribourse.domain.print;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.pluribourse.domain.edition.dto.*;
import org.pluribourse.domain.instanceconfig.dto.*;
import org.pluribourse.domain.item.dto.*;
import org.pluribourse.domain.item.entity.*;
import org.pluribourse.domain.item.repository.*;
import org.pluribourse.domain.pos.dto.*;
import org.pluribourse.domain.pos.entity.*;
import org.pluribourse.domain.pos.repository.*;
import org.pluribourse.domain.print.dto.*;
import org.pluribourse.domain.print.entity.*;
import org.pluribourse.domain.print.service.*;
import org.pluribourse.domain.seller.dto.*;
import org.pluribourse.domain.user.enums.*;
import org.pluribourse.shared.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.*;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.*;

import java.io.IOException;
import java.math.*;
import java.nio.charset.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Story 4.5: printing the buyer invoice PDF for an already-validated {@link Sale}. Distinct
 * business scenario from {@code PosBasketIT} (basket CRUD) — printer registration/selection,
 * {@link PrinterBridgeDouble}, direct calls on the real renderer — same reasoning as
 * {@code DepositSlipPrintingIT} being separate from the deposit CRUD tests (see that class's
 * Javadoc for why AC content is verified with direct calls on the real, fully-wired
 * {@link InvoiceRenderer}/{@link DocumentPrintService} beans rather than through a controller that
 * exposes no raw PDF bytes).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InvoicePrintingIT extends IntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private SaleRepository saleRepository;
    @Autowired
    private InvoiceRenderer invoiceRenderer;
    @Autowired
    private DepositSlipRenderer depositSlipRenderer;
    @Autowired
    private SettlementReportRenderer settlementReportRenderer;
    @Autowired
    private DailyReportRenderer dailyReportRenderer;
    @Autowired
    private EditionReportRenderer editionReportRenderer;
    @Autowired
    private PrintQueueService printQueueService;

    private static PrinterBridgeDouble printerBridgeDouble;

    private static final DateTimeFormatter SOLD_AT_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String ASSOCIATION_NAME = "Association Facture Test";
    private static final String EDITION_NAME = "Bourse Facture 2026";

    private static final String ITEM_BARCODE = "00010001"; // Peluche, 7.00
    private static final String LOT_ITEM_1_BARCODE = "00010002"; // Lot Duo, global 12.00
    private static final String LOT_ITEM_2_BARCODE = "00010003";

    private MockHttpSession adminSession;
    private MockHttpSession volunteer1Session;
    private MockHttpSession volunteer2Session;
    private Long editionId;
    private Long categoryId;
    private Long sellerAId;
    private Long a4PrinterId;
    private Long saleId;

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
    void setUpSessionsAndEdition() throws Exception {
        MvcResult adminLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "test_admin")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andReturn();
        adminSession = (MockHttpSession) adminLogin.getRequest().getSession(false);

        MvcResult editionResult = mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditionDto(null, EDITION_NAME,
                                null, new BigDecimal("10.00"), Language.FR, null, false,
                                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3), null))))
                .andExpect(status().isCreated())
                .andReturn();
        editionId = objectMapper.readValue(editionResult.getResponse().getContentAsString(), EditionDto.class).id();

        MvcResult categoriesResult = mockMvc.perform(put("/api/admin/editions/" + editionId + "/categories")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(new EditionCategoryDto(null, "Jouets", List.of(1))))))
                .andExpect(status().isOk())
                .andReturn();
        categoryId = extractFirstCategoryId(categoriesResult);

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

    private Long extractFirstCategoryId(MvcResult categoriesResult) throws Exception {
        List<EditionCategoryDto> categories = objectMapper.readValue(
                categoriesResult.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, EditionCategoryDto.class));
        return categories.getFirst().id();
    }

    @Test
    @Order(1)
    void advance_edition_to_deposit_phase() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("DEPOSIT"));
    }

    @Test
    @Order(2)
    void create_seller_with_standalone_item_and_lot() throws Exception {
        SellerDto sellerPayload = new SellerDto(null, "Alice", "Vendeuse", "alice.facture@email.com", "0600000001");
        MvcResult sellerResult = mockMvc.perform(post("/api/sellers")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sellerPayload)))
                .andExpect(status().isCreated())
                .andReturn();
        sellerAId = objectMapper.readValue(sellerResult.getResponse().getContentAsString(), SellerDto.class).id();

        CreateItemDto itemPayload = new CreateItemDto(sellerAId, categoryId, "Peluche", new BigDecimal("7.00"), false, null);
        mockMvc.perform(post("/api/items")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(itemPayload)))
                .andExpect(status().isCreated());

        CreateLotDto lotPayload = new CreateLotDto(sellerAId, "Lot Duo", new BigDecimal("12.00"), List.of(
                new CreateLotItemDto(categoryId, "Piece A", false, null),
                new CreateLotItemDto(categoryId, "Piece B", false, null)
        ));
        mockMvc.perform(post("/api/lots")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lotPayload)))
                .andExpect(status().isCreated());
    }

    @Test
    @Order(3)
    void register_a4_printer_and_select_it_for_volunteer1() throws Exception {
        printerBridgeDouble.register("bridge-invoice-a4", "A4 Facture Test", "NETWORK", "ONLINE");
        MvcResult a4Result = mockMvc.perform(post("/api/admin/printers")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePrinterDto(
                                "A4 Facture Test", PrinterType.A4, null, "bridge-invoice-a4"))))
                .andExpect(status().isCreated())
                .andReturn();
        a4PrinterId = objectMapper.readValue(a4Result.getResponse().getContentAsString(), PrinterDto.class).id();

        mockMvc.perform(post("/api/printers/selection")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"thermalPrinterId\":null,\"a4PrinterId\":" + a4PrinterId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.a4PrinterId").value(a4PrinterId));
    }

    @Test
    @Order(4)
    void set_association_name_in_instance_config() throws Exception {
        mockMvc.perform(put("/api/admin/instance-config")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new GlobalInstanceConfigDto(ASSOCIATION_NAME, new BigDecimal("10.00"), Language.FR))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.associationName").value(ASSOCIATION_NAME));
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
    void scan_and_validate_a_real_sale() throws Exception {
        MvcResult basketResult = mockMvc.perform(get("/api/pos/baskets/current").session(volunteer1Session))
                .andExpect(status().isOk())
                .andReturn();
        Long basketId = objectMapper.readValue(basketResult.getResponse().getContentAsString(), BasketDto.class).id();

        addItem(basketId, ITEM_BARCODE);
        addItem(basketId, LOT_ITEM_1_BARCODE);
        addItem(basketId, LOT_ITEM_2_BARCODE);

        ValidateBasketDto payload = new ValidateBasketDto(PaymentMethod.CASH, null);
        MvcResult validateResult = mockMvc.perform(post("/api/pos/baskets/" + basketId + "/validate")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn();
        SaleDto sale = objectMapper.readValue(validateResult.getResponse().getContentAsString(), SaleDto.class);
        assertThat(sale.total()).isEqualByComparingTo("19.00");
        saleId = sale.id();
    }

    private void addItem(Long basketId, String barcode) throws Exception {
        mockMvc.perform(post("/api/pos/baskets/" + basketId + "/items")
                        .session(volunteer1Session).with(csrf())
                        .param("barcode", barcode))
                .andExpect(status().isOk());
    }

    @Test
    @Order(7)
    @Transactional(readOnly = true)
        // read-only, no HTTP writes below: safe to keep the session open for lazy access
    void invoice_renderer_deduplicates_lot_line_and_includes_association_edition_and_date() {
        List<Item> items = itemRepository.findAllBySaleIdOrderById(saleId);
        Sale sale = saleRepository.findById(saleId).orElseThrow();

        byte[] pdf = invoiceRenderer.renderInvoice(ASSOCIATION_NAME, EDITION_NAME, sale.getSoldAt(), items, Locale.FRENCH);
        String rendered = new String(pdf, StandardCharsets.ISO_8859_1);

        assertThat(rendered).startsWith("%PDF");
        assertThat(rendered).contains("Peluche").contains("7.00");
        // The lot has 2 member items but must appear on a single line (FR-041): its name and
        // global price each occur exactly once, never duplicated per member item.
        assertThat(countOccurrences(rendered, "Lot Duo")).isEqualTo(1);
        assertThat(countOccurrences(rendered, "12.00")).isEqualTo(1);
        assertThat(rendered).contains(ASSOCIATION_NAME).contains(EDITION_NAME);
        assertThat(rendered).contains(sale.getSoldAt().format(SOLD_AT_FORMATTER));
        // total = 7.00 + 12.00 = 19.00
        assertThat(rendered).contains("19.00");
    }

    @Test
    @Order(8)
    @Transactional(readOnly = true)
        // Same eager-loading constraint as production (Dev Notes § Chargement eager): execute()
        // below runs synchronously in this test thread, but the pattern (touching lazy
        // associations before execute()) mirrors the real queue consumer thread's requirement.
    void document_print_service_sends_the_rendered_invoice_pdf_bytes_via_printer_bridge_client() {
        List<Item> items = itemRepository.findAllBySaleIdOrderById(saleId);
        Sale sale = saleRepository.findById(saleId).orElseThrow();

        PrinterBridgeClient mockClient = mock(PrinterBridgeClient.class);
        DocumentPrintService documentPrintService =
                new DocumentPrintService(depositSlipRenderer, invoiceRenderer, settlementReportRenderer, dailyReportRenderer,
                        editionReportRenderer, mockClient);

        Printer printer = new Printer();
        printer.setPrinterBridgeId("bridge-invoice-mock-target");
        documentPrintService.buildInvoiceJob(ASSOCIATION_NAME, EDITION_NAME, sale.getSoldAt(), items, Locale.FRENCH).execute(printer);

        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(mockClient).print(eq("bridge-invoice-mock-target"), eq(PrintContentType.PDF), payloadCaptor.capture());
        assertThat(new String(payloadCaptor.getValue(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    @Test
    @Order(9)
    void printing_the_invoice_via_http_is_queued_reaches_printer_bridge_client_and_stays_reprintable() throws Exception {
        mockMvc.perform(post("/api/pos/sales/" + saleId + "/invoice/print")
                        .session(volunteer1Session).with(csrf()))
                .andExpect(status().isNoContent());

        // A 204 only proves the job was queued, not that it actually executed — the PDF render
        // happens later, on the queue's own consumer thread. This test uses the REAL, Spring-wired
        // PrinterBridgeClient (not mocked, unlike Order 8) — so the invoice job genuinely attempts
        // a WebSocket connection to PrinterBridgeDouble, which is HTTP-only and cannot complete the
        // WS handshake. The job therefore fails and suspends this printer's queue — expected here,
        // proving the HTTP-triggered production path (controller -> service -> real
        // PrinterBridgeClient) runs end to end without throwing before reaching
        // PrinterBridgeClient — no LazyInitializationException, no wrong wiring.
        waitUntil(() -> printQueueService.getHandle(a4PrinterId).isSuspended());
        assertThat(printQueueService.getHandle(a4PrinterId).getLastError()).isNotNull();

        // AC4 (reprintable): no "already printed" state anywhere in PosInvoicePrintService — a
        // second print request for the same sale must still be accepted, not rejected because it
        // was "already printed". Discard (not resume) the failed job first so the queue is
        // deterministically available again — resume would retry the same doomed job and
        // re-suspend the queue at an unpredictable moment, racing this assertion.
        mockMvc.perform(post("/api/admin/print-queue/" + a4PrinterId + "/discard")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/pos/sales/" + saleId + "/invoice/print")
                        .session(volunteer1Session).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(10)
    void printing_another_volunteers_sale_returns_404_ownership_is_never_confirmed_or_denied() throws Exception {
        mockMvc.perform(post("/api/pos/sales/" + saleId + "/invoice/print")
                        .session(volunteer2Session).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/sale-not-found")));
    }

    @Test
    @Order(11)
    void printing_a_nonexistent_sale_returns_404() throws Exception {
        mockMvc.perform(post("/api/pos/sales/999999/invoice/print")
                        .session(volunteer1Session).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/sale-not-found")));
    }

    @Test
    @Order(12)
    void printing_without_an_a4_printer_selected_returns_422() throws Exception {
        // Clears volunteer1's A4 selection (the sale is still theirs, ownership passes) — proves
        // the printer-availability check independently of the IDOR check above.
        mockMvc.perform(post("/api/printers/selection")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"thermalPrinterId\":null,\"a4PrinterId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.a4PrinterId").isEmpty());

        mockMvc.perform(post("/api/pos/sales/" + saleId + "/invoice/print")
                        .session(volunteer1Session).with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/invalid-printer-selection")));
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
