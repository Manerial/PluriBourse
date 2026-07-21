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
import org.springframework.beans.factory.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.*;

import java.io.*;
import java.math.*;
import java.net.*;
import java.nio.charset.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Story 3.6 Dev Notes: exactly like {@code ThermalLabelPrintingIT}, no THERMAL printer can ever
 * pass its connectivity check in this environment (no real serial hardware) — a registered THERMAL
 * printer therefore always ends up "unavailable". Since {@code validateDeposit()} checks the
 * thermal selection before the A4 one (Dev Notes § Ordre de validation), every HTTP-level
 * validation scenario in this class necessarily fails on the thermal leg; there is no way to
 * exercise the "A4 unavailable while thermal available" branch nor the "both jobs submitted" happy
 * path through the real queue in this environment. AC1/AC6 (the PDF job is actually built, queued
 * and delivered) and the rendered content (AC3-5) are instead verified with direct calls on the
 * real, fully-wired {@link DocumentPrintService}/{@link DepositSlipRenderer} beans — same justified
 * exception as {@code PrintInfrastructureIT}/{@code ThermalLabelPrintingIT} (no controller exposes
 * raw PDF bytes or queue submission outcomes).
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
    private DocumentPrintService documentPrintService;
    @Autowired
    private DepositSlipRenderer depositSlipRenderer;
    @Autowired
    private PrintQueueService printQueueService;

    private static ServerSocket reachableA4Target;

    private MockHttpSession adminSession;
    private MockHttpSession volunteerSession;
    private Long editionId;
    private Long categoryId;
    private Long sellerAId;
    private Long emptySellerId;
    private Long a4PrinterId;

    @BeforeAll
    void setUpSessionsAndEdition() throws Exception {
        reachableA4Target = new ServerSocket(0);

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

    @AfterAll
    void tearDownTarget() throws Exception {
        reachableA4Target.close();
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
    void register_a4_and_unavailable_thermal_printers() throws Exception {
        MvcResult a4Result = mockMvc.perform(post("/api/admin/printers")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePrinterDto(
                                "A4 Bordereau Test", PrinterType.A4, null, null, "127.0.0.1", reachableA4Target.getLocalPort()))))
                .andExpect(status().isCreated())
                .andReturn();
        a4PrinterId = objectMapper.readValue(a4Result.getResponse().getContentAsString(), PrinterDto.class).id();
    }

    @Test
    @Order(4)
    void validate_deposit_without_any_printer_selected_returns_422() throws Exception {
        mockMvc.perform(post("/api/sellers/" + sellerAId + "/deposit/validate")
                        .session(volunteerSession).with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/invalid-printer-selection")));
    }

    @Test
    @Order(5)
    void select_valid_a4_printer_then_validate_deposit_still_fails_on_missing_thermal() throws Exception {
        mockMvc.perform(post("/api/printers/selection")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"thermalPrinterId\":null,\"a4PrinterId\":" + a4PrinterId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.a4PrinterId").value(a4PrinterId));

        // Both selections are validated before either job is submitted (AC2, Dev Notes § Ordre de
        // validation): a genuinely available A4 printer must not let a missing thermal selection
        // through.
        mockMvc.perform(post("/api/sellers/" + sellerAId + "/deposit/validate")
                        .session(volunteerSession).with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/invalid-printer-selection")));
    }

    @Test
    @Order(6)
    void register_thermal_printer_and_validate_deposit_still_fails_since_it_is_unreachable() throws Exception {
        MvcResult printerResult = mockMvc.perform(post("/api/admin/printers")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePrinterDto("Thermique Bordereau Test", PrinterType.THERMAL, "COM_TEST_98", 57, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        Long thermalPrinterId = objectMapper.readValue(printerResult.getResponse().getContentAsString(), PrinterDto.class).id();

        // No real serial hardware in this environment (see class Javadoc): writing the session
        // attribute directly simulates a printer that was available at selection time and has
        // since become unavailable — same bypass technique as ThermalLabelPrintingIT Order(12).
        volunteerSession.setAttribute("printerSelection.thermalPrinterId", thermalPrinterId);
        volunteerSession.setAttribute("printerSelection.done", true);

        mockMvc.perform(post("/api/sellers/" + sellerAId + "/deposit/validate")
                        .session(volunteerSession).with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/invalid-printer-selection")));
    }

    @Test
    @Order(7)
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
    @Order(8)
    @Transactional(readOnly = true)
        // Same eager-loading constraint as production (Dev Notes § Chargement eager): execute()
        // below runs on the print job's own executor thread, after this method's transaction has
        // returned control — seller.getEdition() must be initialized here, on the thread that
        // still holds the Hibernate session, exactly like EditionScopedLookup.findSellerInEdition
        // already does for the real validateDeposit()/reprintDepositSlip() flow.
    void document_print_service_delivers_the_rendered_pdf_bytes_to_the_reachable_printer() throws Exception {
        List<Item> items = itemRepository.findAllBySellerProfileIdOrderByItemNumberAsc(sellerAId);
        SellerProfile seller = sellerRepository.findById(sellerAId).orElseThrow();
        seller.getEdition().getName();

        try (ServerSocket contentServer = new ServerSocket(0);
             ExecutorService acceptExecutor = Executors.newSingleThreadExecutor()) {
            Future<byte[]> received = acceptExecutor.submit(() -> {
                try (Socket socket = contentServer.accept()) {
                    return socket.getInputStream().readAllBytes();
                }
            });

            Printer printer = new Printer();
            printer.setHost("127.0.0.1");
            printer.setPort(contentServer.getLocalPort());
            documentPrintService.buildDepositSlipJob(seller, items, new BigDecimal("10.00"), Locale.FRENCH).execute(printer);

            byte[] bytes = received.get(5, TimeUnit.SECONDS);
            assertThat(new String(bytes, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        }
    }

    @Test
    @Order(9)
    void reprint_deposit_slip_for_seller_with_no_items_returns_422() throws Exception {
        mockMvc.perform(post("/api/sellers/" + emptySellerId + "/deposit/slip/reprint")
                        .session(volunteerSession).with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/empty-deposit")));
    }

    @Test
    @Order(10)
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
    @Order(11)
    void advance_edition_to_sale_phase() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("SALE"));
    }

    @Test
    @Order(12)
    void reprint_deposit_slip_outside_deposit_or_post_sale_phase_is_blocked() throws Exception {
        mockMvc.perform(post("/api/sellers/" + sellerAId + "/deposit/slip/reprint")
                        .session(volunteerSession).with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/deposit-reprint-not-allowed")));
    }

    @Test
    @Order(13)
    void advance_edition_to_post_sale_phase() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("POST_SALE"));
    }

    @Test
    @Order(14)
    void reprint_deposit_slip_in_post_sale_phase_with_a4_selected_succeeds() throws Exception {
        // volunteerSession still holds the A4 selection from Order(5) — the thermal printer's
        // unavailability (Order 6) is irrelevant here: reprintDepositSlip() never checks it.
        mockMvc.perform(post("/api/sellers/" + sellerAId + "/deposit/slip/reprint")
                        .session(volunteerSession).with(csrf()))
                .andExpect(status().isNoContent());

        // A 204 only proves the job was queued, not that it actually executed: the PDF render
        // happens later, on the queue's own consumer thread, well after this request's
        // transaction (and EditionScopedLookup.findSellerInEdition's implicit lazy-init of
        // sellerProfile.getEdition()) has returned. A trivial marker job submitted right after
        // runs strictly after the slip job (same printer, FIFO consumer — PrintInfrastructureIT
        // "jobs execute sequentially"): waiting for it proves the slip job already completed,
        // via the real HTTP-triggered path — not just the direct-bean call in Order(8) — without
        // a LazyInitializationException suspending the queue.
        AtomicBoolean markerRan = new AtomicBoolean(false);
        printQueueService.submit(a4PrinterId, printer -> markerRan.set(true));
        waitUntil(() -> markerRan.get() || printQueueService.getHandle(a4PrinterId).isSuspended());
        assertThat(printQueueService.getHandle(a4PrinterId).isSuspended()).isFalse();
        assertThat(printQueueService.getHandle(a4PrinterId).getLastError()).isNull();
    }

    @Test
    @Order(15)
    @Transactional(readOnly = true)
    void deposit_slip_renderer_rounds_net_amount_half_up_at_an_exact_tie() {
        // Independent of the shared sellerAId state above: total=10.00, rate=0.75% gives a raw net
        // of 9.9250 — an exact tie at the 3rd decimal (HALF_UP -> 9.93, HALF_EVEN -> 9.92) that the
        // Order(7) scenario (19.00 at 10%) never exercises, since 19.00 * 10 / 100 divides evenly
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
