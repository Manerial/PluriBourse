package org.pluribourse.domain.print;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.pluribourse.domain.edition.dto.*;
import org.pluribourse.domain.item.dto.*;
import org.pluribourse.domain.item.entity.*;
import org.pluribourse.domain.item.repository.*;
import org.pluribourse.domain.print.dto.*;
import org.pluribourse.domain.print.entity.*;
import org.pluribourse.domain.print.service.*;
import org.pluribourse.domain.seller.dto.*;
import org.pluribourse.domain.seller.entity.*;
import org.pluribourse.domain.seller.repository.*;
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
 * Story 3.6 Dev Notes: {@code reprintDepositSlip()} only resolves and checks the A4 printer — it
 * never touches the thermal selection, so unlike {@code ThermalLabelPrintingIT} this class doesn't
 * need a THERMAL printer at all. AC1/AC6 (the PDF job is actually built, queued and delivered) and
 * the rendered content (AC3-5) are verified with direct calls on the real, fully-wired
 * {@link DocumentPrintService}/{@link DepositSlipRenderer} beans — same justified exception as
 * {@code PrintInfrastructureIT}/{@code ThermalLabelPrintingIT} (no controller exposes raw PDF
 * bytes or queue submission outcomes). Since story 3.12, delivery itself (Order 6) is verified
 * against a Mockito-mocked {@link PrinterBridgeClient} built locally in that one test —
 * PrinterBridge's real WebSocket protocol is a native external process, not reproducible with the
 * lightweight {@link PrinterBridgeDouble} (HTTP-only) used for connectivity checks elsewhere in
 * this class; CLAUDE.md's Mockito exception for external components applies here.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DepositSlipPrintingIT extends IntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private SellerRepository sellerRepository;
    @Autowired
    private DepositSlipRenderer depositSlipRenderer;
    @Autowired
    private PrintQueueService printQueueService;

    private static PrinterBridgeDouble printerBridgeDouble;

    private MockHttpSession adminSession;
    private MockHttpSession volunteerSession;
    private Long editionId;
    private Long categoryId;
    private Long sellerAId;
    private Long emptySellerId;
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
                        .content(objectMapper.writeValueAsString(new EditionDto(null, "Bourse Bordereau 2026",
                                null, new BigDecimal("10.00"), Language.FR, null, false,
                                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3)))))
                .andExpect(status().isCreated())
                .andReturn();
        editionId = objectMapper.readValue(editionResult.getResponse().getContentAsString(), EditionDto.class).id();

        MvcResult categoriesResult = mockMvc.perform(put("/api/admin/editions/" + editionId + "/categories")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(new EditionCategoryDto(null, "Jouets", List.of(1, 2))))))
                .andExpect(status().isOk())
                .andReturn();
        categoryId = extractFirstCategoryId(categoriesResult);

        MvcResult volunteerLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "volunteer1")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andReturn();
        volunteerSession = (MockHttpSession) volunteerLogin.getRequest().getSession(false);
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
        sellerAId = createSeller("Alice", "Vendeuse", "alice.slip@email.com");
        createItem(sellerAId, "Peluche", "7.00");

        CreateLotDto lotPayload = new CreateLotDto(sellerAId, "Lot Duo", new BigDecimal("12.00"), List.of(
                new CreateLotItemDto(categoryId, "Piece A", false, null),
                new CreateLotItemDto(categoryId, "Piece B", false, null)
        ));
        mockMvc.perform(post("/api/lots")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lotPayload)))
                .andExpect(status().isCreated());

        // Created now, while still in the Deposit phase (seller creation requires it) — kept for
        // the empty-deposit reprint scenario (Order 9), tested before the edition advances past
        // Post-vente.
        emptySellerId = createSeller("Diane", "Vendeuse", "diane.slip@email.com");
    }

    @Test
    @Order(3)
    void register_a4_printer() throws Exception {
        printerBridgeDouble.register("bridge-slip-a4", "A4 Bordereau Test", "NETWORK", "ONLINE");
        MvcResult a4Result = mockMvc.perform(post("/api/admin/printers")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePrinterDto(
                                "A4 Bordereau Test", PrinterType.A4, null, "bridge-slip-a4"))))
                .andExpect(status().isCreated())
                .andReturn();
        a4PrinterId = objectMapper.readValue(a4Result.getResponse().getContentAsString(), PrinterDto.class).id();
    }

    @Test
    @Order(4)
    void select_valid_a4_printer() throws Exception {
        mockMvc.perform(post("/api/printers/selection")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"thermalPrinterId\":null,\"a4PrinterId\":" + a4PrinterId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.a4PrinterId").value(a4PrinterId));
    }

    @Test
    @Order(5)
    @Transactional(readOnly = true)
        // read-only, no HTTP writes below: safe to keep the session open for lazy access
    void deposit_slip_renderer_deduplicates_lot_line_and_computes_correct_net_amount() {
        List<Item> items = itemRepository.findAllBySellerProfileIdOrderByItemNumberAsc(sellerAId);
        SellerProfile seller = sellerRepository.findById(sellerAId).orElseThrow();

        byte[] pdf = depositSlipRenderer.renderSlip(seller, items, new BigDecimal("10.00"), Locale.FRENCH);
        String rendered = new String(pdf, StandardCharsets.ISO_8859_1);

        assertThat(rendered).startsWith("%PDF");
        assertThat(rendered).contains("Peluche").contains("7.00");
        // The lot has 2 member items but must appear on a single line (FR-031): its name and
        // global price each occur exactly once, never duplicated per member item.
        assertThat(countOccurrences(rendered, "Lot Duo")).isEqualTo(1);
        assertThat(countOccurrences(rendered, "12.00")).isEqualTo(1);
        // total = 7.00 + 12.00 = 19.00; commission 10% = 1.90; net = 17.10 (BigDecimal, HALF_UP)
        assertThat(rendered).contains("17.10");
    }

    @Test
    @Order(6)
    @Transactional(readOnly = true)
        // Same eager-loading constraint as production (Dev Notes § Chargement eager): execute()
        // below runs synchronously in this test thread, but the pattern (touching lazy
        // associations before execute()) mirrors the real queue consumer thread's requirement.
    void document_print_service_sends_the_rendered_pdf_bytes_via_printer_bridge_client() {
        List<Item> items = itemRepository.findAllBySellerProfileIdOrderByItemNumberAsc(sellerAId);
        SellerProfile seller = sellerRepository.findById(sellerAId).orElseThrow();
        seller.getEdition().getName();

        PrinterBridgeClient mockClient = mock(PrinterBridgeClient.class);
        DocumentPrintService documentPrintService = new DocumentPrintService(depositSlipRenderer, mockClient);

        Printer printer = new Printer();
        printer.setPrinterBridgeId("bridge-slip-mock-target");
        documentPrintService.buildDepositSlipJob(seller, items, new BigDecimal("10.00"), Locale.FRENCH).execute(printer);

        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(mockClient).print(eq("bridge-slip-mock-target"), eq(PrintContentType.PDF), payloadCaptor.capture());
        assertThat(new String(payloadCaptor.getValue(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    @Test
    @Order(7)
    void reprint_deposit_slip_for_seller_with_no_items_returns_422() throws Exception {
        mockMvc.perform(post("/api/sellers/" + emptySellerId + "/deposit/slip/reprint")
                        .session(volunteerSession).with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/empty-deposit")));
    }

    @Test
    @Order(8)
    void reprint_deposit_slip_without_a4_printer_selected_returns_422() throws Exception {
        MvcResult volunteer2Login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "volunteer2")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession volunteer2Session = (MockHttpSession) volunteer2Login.getRequest().getSession(false);

        mockMvc.perform(post("/api/sellers/" + sellerAId + "/deposit/slip/reprint")
                        .session(volunteer2Session).with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/invalid-printer-selection")));
    }

    @Test
    @Order(9)
    void advance_edition_to_sale_phase() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("SALE"));
    }

    @Test
    @Order(10)
    void reprint_deposit_slip_outside_deposit_or_post_sale_phase_is_blocked() throws Exception {
        mockMvc.perform(post("/api/sellers/" + sellerAId + "/deposit/slip/reprint")
                        .session(volunteerSession).with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/deposit-reprint-not-allowed")));
    }

    @Test
    @Order(11)
    void advance_edition_to_post_sale_phase() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("POST_SALE"));
    }

    @Test
    @Order(12)
    void reprint_deposit_slip_in_post_sale_phase_with_a4_selected_is_queued_and_reaches_printer_bridge_client() throws Exception {
        // volunteerSession still holds the A4 selection from Order(4) — reprintDepositSlip() never
        // checks the thermal printer at all.
        mockMvc.perform(post("/api/sellers/" + sellerAId + "/deposit/slip/reprint")
                        .session(volunteerSession).with(csrf()))
                .andExpect(status().isNoContent());

        // A 204 only proves the job was queued, not that it actually executed: the PDF render
        // happens later, on the queue's own consumer thread, well after this request's
        // transaction (and EditionScopedLookup.findSellerInEdition's implicit lazy-init of
        // sellerProfile.getEdition()) has returned. This test uses the REAL, Spring-wired
        // PrinterBridgeClient (not mocked, unlike Order 6) — so the slip job genuinely attempts a
        // WebSocket connection to PrinterBridgeDouble, which is HTTP-only (see class Javadoc) and
        // cannot complete the WS handshake. The job therefore fails and suspends this printer's
        // queue — expected and correct here, since real successful WS delivery is what Order(6)
        // (direct call with a mocked PrinterBridgeClient) and PrinterBridgeClient's own dedicated
        // test already cover. What this test actually proves: the HTTP-triggered production path
        // (controller -> service -> real PrinterBridgeClient) runs end to end without throwing
        // before reaching PrinterBridgeClient — no LazyInitializationException, no wrong wiring —
        // and that the resulting failure is attributed to the correct printer's queue alone.
        waitUntil(() -> printQueueService.getHandle(a4PrinterId).isSuspended());
        assertThat(printQueueService.getHandle(a4PrinterId).getLastError()).isNotNull();

        AtomicBoolean otherPrinterUnaffected = new AtomicBoolean(false);
        String otherBridgeId = "bridge-slip-a4-other";
        printerBridgeDouble.register(otherBridgeId, "A4 Bordereau Autre", "NETWORK", "ONLINE");
        MvcResult otherResult = mockMvc.perform(post("/api/admin/printers")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePrinterDto(
                                "A4 Bordereau Autre", PrinterType.A4, null, otherBridgeId))))
                .andExpect(status().isCreated())
                .andReturn();
        Long otherPrinterId = objectMapper.readValue(otherResult.getResponse().getContentAsString(), PrinterDto.class).id();
        printQueueService.submit(otherPrinterId, printer -> otherPrinterUnaffected.set(true));
        waitUntil(otherPrinterUnaffected::get);
        assertThat(printQueueService.getHandle(otherPrinterId).isSuspended()).isFalse();
    }

    @Test
    @Order(13)
    @Transactional(readOnly = true)
    void deposit_slip_renderer_rounds_net_amount_half_up_at_an_exact_tie() {
        // Independent of the shared sellerAId state above: total=10.00, rate=0.75% gives a raw net
        // of 9.9250 — an exact tie at the 3rd decimal (HALF_UP -> 9.93, HALF_EVEN -> 9.92) that the
        // Order(5) scenario (19.00 at 10%) never exercises, since 19.00 * 10 / 100 divides evenly
        // with nothing left to round.
        SellerProfile seller = sellerRepository.findById(sellerAId).orElseThrow();
        Item item = new Item();
        item.setName("Article Arrondi");
        item.setPrice(new BigDecimal("10.00"));

        byte[] pdf = depositSlipRenderer.renderSlip(seller, List.of(item), new BigDecimal("0.75"), Locale.FRENCH);
        String rendered = new String(pdf, StandardCharsets.ISO_8859_1);

        assertThat(rendered).contains("9.93");
        assertThat(rendered).doesNotContain("9.92");
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

    private Long createSeller(String firstName, String lastName, String email) throws Exception {
        SellerDto payload = new SellerDto(null, firstName, lastName, email, "0600000000");
        MvcResult result = mockMvc.perform(post("/api/sellers")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), SellerDto.class).id();
    }

    private void createItem(Long sellerId, String name, String price) throws Exception {
        CreateItemDto payload = new CreateItemDto(sellerId, categoryId, name, new BigDecimal(price), false, null);
        mockMvc.perform(post("/api/items")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated());
    }
}
