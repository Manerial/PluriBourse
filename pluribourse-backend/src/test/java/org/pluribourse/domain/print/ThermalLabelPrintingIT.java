package org.pluribourse.domain.print;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.pluribourse.domain.edition.dto.*;
import org.pluribourse.domain.item.dto.*;
import org.pluribourse.domain.item.entity.*;
import org.pluribourse.domain.item.repository.*;
import org.pluribourse.domain.print.dto.*;
import org.pluribourse.domain.print.entity.*;
import org.pluribourse.domain.print.repository.*;
import org.pluribourse.domain.print.service.*;
import org.pluribourse.domain.seller.dto.*;
import org.pluribourse.domain.seller.entity.*;
import org.pluribourse.domain.seller.repository.*;
import org.pluribourse.shared.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.*;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.*;

import java.io.*;
import java.math.*;
import java.nio.charset.*;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Story 3.5 Dev Notes: no THERMAL printer can ever pass its connectivity check in this environment
 * (no real serial hardware, gap already accepted since story 3.4/3.9 — see {@code PrinterSelectionIT}).
 * A THERMAL printer registered here therefore always ends up "unavailable" — used on purpose to
 * exercise the labels-reprint error branch. The actual rendering content (barcode, no seller
 * name, lot position) is asserted by calling the real, fully-wired {@link ThermalLabelRenderer}
 * bean directly on entities persisted through the controllers — same justified exception as
 * {@code PrintInfrastructureIT} (no controller can expose rendered bytes; the Spring context is the
 * genuine integration context, this is not an isolated unit test). Since story 3.11/3.12,
 * connectivity and delivery both go through {@link PrinterBridgeClient} — see
 * {@link PrinterBridgeDouble} for the fake PrinterBridge process backing
 * {@code printerbridge.base-url} in this class.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ThermalLabelPrintingIT extends IntegrationTest {

    private static final Charset LABEL_CHARSET = Charset.forName("Cp858");

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private SellerRepository sellerRepository;
    @Autowired
    private PrinterRepository printerRepository;
    @Autowired
    private ThermalLabelRenderer renderer;
    @Autowired
    private ThermalPrintService thermalPrintService;

    private static PrinterBridgeDouble printerBridgeDouble;

    private MockHttpSession adminSession;
    private MockHttpSession volunteerSession;
    private Long editionId;
    private Long categoryId;
    private Long sellerAId;
    private Long unavailablePrinterId;

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

        MvcResult editionResult = mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditionDto(null, "Bourse Etiquettes 2026", null, null, null, null, false, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3), null, null))))
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
    void reprint_labels_before_any_active_edition_returns_404() throws Exception {
        // No seller can exist yet (SellerService also requires DEPOSIT phase).
        // Story 2.10 : editionId est encore en PREPARATION ici — PhaseType.ACTIVE ne la couvre plus
        // (AC 4), donc DepositValidationService échoue désormais dès editionService.getActiveEdition()
        // (404 no-active-edition), avant même d'atteindre la résolution du vendeur ou le contrôle de
        // phase. Ce test n'exerce donc PAS la garde de phase (le refus "hors phase Dépôt" est couvert
        // par reprint_labels_in_post_sale_phase_is_blocked, @Order(20)). Le résultat métier reste
        // identique (réimpression bloquée).
        mockMvc.perform(post("/api/sellers/1/deposit/labels/reprint")
                        .session(volunteerSession).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/no-active-edition")));
    }

    @Test
    @Order(2)
    void advance_edition_to_deposit_phase() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("DEPOSIT"));
    }

    @Test
    @Order(3)
    void create_two_sellers_assigns_sequential_seller_numbers() throws Exception {
        sellerAId = createSeller("Alice", "Vendeuse", "alice.labels@email.com");
        Long sellerBId = createSeller("Bruno", "Vendeur", "bruno.labels@email.com");

        assertThat(sellerRepository.findById(sellerAId).orElseThrow().getSellerNumber()).isEqualTo(1);
        assertThat(sellerRepository.findById(sellerBId).orElseThrow().getSellerNumber()).isEqualTo(2);

        // Bruno has no items yet — deletable in DEPOSIT phase (FR-021). Deleting then recreating a
        // seller must never reuse seller number 2 — Edition.nextSellerNumber is a persisted counter,
        // never recomputed from surviving rows, precisely to prevent this reuse.
        mockMvc.perform(delete("/api/admin/sellers/" + sellerBId).session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());
        Long sellerCId = createSeller("Chloe", "Vendeuse", "chloe.labels@email.com");
        assertThat(sellerRepository.findById(sellerCId).orElseThrow().getSellerNumber()).isEqualTo(3);
    }

    @Test
    @Order(4)
    void create_individual_items_assigns_sequential_item_numbers_and_barcodes() throws Exception {
        createItem(sellerAId, "Peluche", "5.00", false, null);
        createItem(sellerAId, "Casse-tete", "3.00", true, "Piece manquante");

        // Fetched via the eager-fetch print-order query (not plain findById): getBarcode() below
        // dereferences the lazy sellerProfile association, which a bare findById() would leave
        // uninitialized once this method's implicit repository transaction has closed.
        List<Item> items = itemRepository.findAllBySellerProfileIdOrderByItemNumberAsc(sellerAId);
        Item item1 = items.get(0);
        Item item2 = items.get(1);

        assertThat(item1.getItemNumber()).isEqualTo(1);
        assertThat(item2.getItemNumber()).isEqualTo(2);
        assertThat(item1.getBarcode()).isEqualTo("00010001");
        assertThat(item2.getBarcode()).isEqualTo("00010002");
        assertThat(item1.getFormattedBarcode()).isEqualTo("0001-0001");
    }

    @Test
    @Order(5)
    void create_lot_continues_the_same_item_number_sequence() throws Exception {
        CreateLotDto payload = new CreateLotDto(sellerAId, categoryId, "Lot Duo", new BigDecimal("12.00"), List.of(
                new CreateLotItemDto("Piece A", false, null),
                new CreateLotItemDto("Piece B", false, null)
        ));
        MvcResult result = mockMvc.perform(post("/api/lots")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();
        LotDto lot = objectMapper.readValue(result.getResponse().getContentAsString(), LotDto.class);

        Item lotItem1 = itemRepository.findById(lot.items().get(0).id()).orElseThrow();
        Item lotItem2 = itemRepository.findById(lot.items().get(1).id()).orElseThrow();
        assertThat(lotItem1.getItemNumber()).isEqualTo(3);
        assertThat(lotItem2.getItemNumber()).isEqualTo(4);
    }

    @Test
    @Order(6)
    void render_standard_label_contains_barcode_and_no_seller_name() throws Exception {
        List<Item> sellerItems = itemRepository.findAllBySellerProfileIdOrderByItemNumberAsc(sellerAId);
        Item item1 = sellerItems.getFirst();
        byte[] rawBytes = renderer.renderLabel(item1, 57, Locale.FRENCH);
        String rendered = new String(rawBytes, LABEL_CHARSET);

        assertThat(rendered).contains(item1.getFormattedBarcode());
        // Follow-up fix (2026-08-24): the item's actual category name is now printed as the
        // "--- {category} ---" heading, replacing the generic, uninformative word "Category".
        assertThat(rendered).contains("--- Jouets ---").contains("Peluche");
        // Follow-up fix (2026-08-24): the edition name no longer appears on individual article
        // labels — only on the vendor separator (seller_separator_contains_seller_name_and_edition_name).
        assertThat(rendered).doesNotContain("Alice").doesNotContain("Vendeuse").doesNotContain("Bourse Etiquettes 2026");

        // On real hardware, ESC/POS printers default to CP437/CP850, not ISO-8859-15 — the byte
        // ISO-8859-15 uses for € collides with CP437/CP850's ñ. Cp858 (PC858) has both the accented
        // Latin characters and the euro sign; INIT resets the printer's table to its power-on
        // default, so the code page must be explicitly re-selected right after every INIT.
        assertThat(rendered).contains("Peluche - 5.00€");
        assertThat(rawBytes).startsWith(0x1B, 0x40, 0x1B, 0x74, 19);
    }

    @Test
    @Order(7)
    void render_incomplete_label_shows_incomplete_marker() throws Exception {
        List<Item> sellerItems = itemRepository.findAllBySellerProfileIdOrderByItemNumberAsc(sellerAId);
        Item incompleteItem = sellerItems.get(1);
        String rendered = new String(renderer.renderLabel(incompleteItem, 57, Locale.FRENCH), LABEL_CHARSET);
        assertThat(rendered).contains("INCOMPLET");
    }

    @Test
    @Order(8)
    void render_lot_label_shows_bundle_price_and_indivisible_position() {
        List<Item> sellerItems = itemRepository.findAllBySellerProfileIdOrderByItemNumberAsc(sellerAId);
        Item lotItem1 = sellerItems.get(2);
        Item lotItem2 = sellerItems.get(3);

        String renderedFirst = new String(renderer.renderLabel(lotItem1, 57, Locale.FRENCH), LABEL_CHARSET);
        String renderedSecond = new String(renderer.renderLabel(lotItem2, 57, Locale.FRENCH), LABEL_CHARSET);

        assertThat(renderedFirst).contains("Prix du lot : 12.00").contains("Lot indivisible : 1/2");
        assertThat(renderedSecond).contains("Lot indivisible : 2/2");
        // Story 3.14 AC 5: a lot member's category still reflects the LOT's category (Item.category
        // is now copied from Lot.category, never chosen per member) — both labels of "Lot Duo"
        // (created with categoryId, the same "Jouets" category as Order 6's standalone item) must
        // still show the "--- Jouets ---" heading.
        assertThat(renderedFirst).contains("--- Jouets ---");
        assertThat(renderedSecond).contains("--- Jouets ---");
    }

    @Test
    @Order(9)
    void seller_separator_contains_seller_name_and_edition_name() {
        SellerProfile seller = sellerRepository.findById(sellerAId).orElseThrow();
        String rendered = new String(renderer.renderSellerSeparator("Alice Vendeuse", "Bourse Etiquettes 2026"), LABEL_CHARSET);
        assertThat(rendered).contains("Alice Vendeuse").contains("Bourse Etiquettes 2026");
        assertThat(seller.getSellerNumber()).isEqualTo(1); // sanity: still the same seller as Order(3)
    }

    @Test
    @Order(10)
    void article_separator_is_a_partial_cut_command() {
        assertThat(renderer.articleSeparator()).containsExactly(0x1D, 0x56, 0x01);
    }

    @Test
    @Order(11)
    void reprint_labels_without_thermal_printer_selected_returns_422() throws Exception {
        mockMvc.perform(post("/api/sellers/" + sellerAId + "/deposit/labels/reprint")
                        .session(volunteerSession).with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/invalid-printer-selection")));
    }

    @Test
    @Order(12)
    void register_thermal_printer_and_select_it_despite_being_unavailable() throws Exception {
        // Never registered in printerBridgeDouble — PrinterBridgeClient.checkStatus() gets a 404,
        // translated to OFFLINE, so this printer starts "in error", same intent as the previous
        // "no real serial hardware" gap (story 3.4/3.9). Bypassing PrinterSelectionService's own
        // availability gate by writing the session attribute directly simulates a printer that
        // *was* available at selection time (story 3.9) and has since become unavailable (AC3 of
        // this story).
        MvcResult printerResult = mockMvc.perform(post("/api/admin/printers")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePrinterDto("Thermique Test", PrinterType.THERMAL, 57, "bridge-thermal-never-registered"))))
                .andExpect(status().isCreated())
                .andReturn();
        unavailablePrinterId = objectMapper.readValue(printerResult.getResponse().getContentAsString(), PrinterDto.class).id();

        volunteerSession.setAttribute("printerSelection.thermalPrinterId", unavailablePrinterId);
        volunteerSession.setAttribute("printerSelection.done", true);
    }

    @Test
    @Order(13)
    void reprint_labels_with_unavailable_thermal_printer_returns_422() throws Exception {
        mockMvc.perform(post("/api/sellers/" + sellerAId + "/deposit/labels/reprint")
                        .session(volunteerSession).with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/invalid-printer-selection")));
    }

    @Test
    @Order(14)
    @Transactional(readOnly = true)
        // read-only, no HTTP writes in this test: safe to keep the session open for lazy access below
    void thermal_print_job_propagates_printer_bridge_connection_failure_uncaught() {
        // printerBridgeDouble is HTTP-only (see class Javadoc / PrinterBridgeDouble) — it cannot
        // complete a real WebSocket handshake, so PrinterBridgeClient.print() fails the same way
        // it would if PrinterBridge itself were down. Real successful WS delivery is covered by
        // DepositSlipPrintingIT's mocked-client test and PrinterBridgeClient's own dedicated test.
        SellerProfile seller = sellerRepository.findById(sellerAId).orElseThrow();
        List<Item> items = itemRepository.findAllBySellerProfileIdOrderByItemNumberAsc(sellerAId);
        Printer printer = printerRepository.findById(unavailablePrinterId).orElseThrow();

        assertThatThrownBy(() -> thermalPrintService.buildDepositJob(seller, items, Locale.FRENCH).execute(printer))
                .isInstanceOf(org.pluribourse.domain.print.exception.PrinterBridgeUnavailableException.class);
    }

    @Test
    @Order(15)
    void render_label_at_80mm_uses_a_wider_barcode_raster_than_57mm() {
        // AC6: 57mm -> 384 dots (48 bytes/row), 80mm -> 576 dots (72 bytes/row), fixed barcode
        // height of 80px (writeBarcode) — the raster image block alone should grow by exactly
        // (72 - 48) * 80 = 1920 bytes; every other line on the label is width-independent text.
        List<Item> sellerItems = itemRepository.findAllBySellerProfileIdOrderByItemNumberAsc(sellerAId);
        Item item = sellerItems.getFirst();

        byte[] narrow = renderer.renderLabel(item, 57, Locale.FRENCH);
        byte[] wide = renderer.renderLabel(item, 80, Locale.FRENCH);

        assertThat(wide.length - narrow.length).isEqualTo(1920);
    }

    @Test
    @Order(16)
    void reprint_labels_for_seller_with_no_items_returns_422() throws Exception {
        Long emptySellerId = createSeller("Diane", "Vendeuse", "diane.labels@email.com");

        mockMvc.perform(post("/api/sellers/" + emptySellerId + "/deposit/labels/reprint")
                        .session(volunteerSession).with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/empty-deposit")));
    }

    @Test
    @Order(17)
    void roll_end_margin_is_separate_from_the_label_and_not_repeated_per_article() {
        List<Item> sellerItems = itemRepository.findAllBySellerProfileIdOrderByItemNumberAsc(sellerAId);
        Item item = sellerItems.getFirst();

        String rendered = new String(renderer.renderLabel(item, 57, Locale.FRENCH), LABEL_CHARSET);
        // renderLabel keeps a single blank line of baseline spacing after the formatted barcode
        // (its own writeLine newline + one extra LINE_FEED) — the bigger tear margin lives only in
        // rollEnd(), called once by ThermalPrintService after the whole roll, not per article.
        assertThat(rendered).endsWith(item.getFormattedBarcode() + "\n\n");

        assertThat(renderer.rollEnd()).containsExactly(0x0A, 0x0A);
    }

    @Test
    @Order(18)
    @Transactional(readOnly = true)
        // read-only, no HTTP writes below: mutates the in-memory Edition only (never flushed),
        // same reasoning as DepositSlipPrintingIT's currency test.
    void render_label_uses_the_edition_currency_not_a_hardcoded_symbol() {
        List<Item> sellerItems = itemRepository.findAllBySellerProfileIdOrderByItemNumberAsc(sellerAId);
        Item item = sellerItems.getFirst();
        item.getEdition().setCurrency("$");

        String rendered = new String(renderer.renderLabel(item, 57, Locale.FRENCH), LABEL_CHARSET);

        // "$" is plain ASCII (unlike "€", never assertable as a literal character here — see
        // Order(6)'s comment) — its presence proves the renderer used the edition's currency, not
        // a symbol baked into the template.
        assertThat(rendered).contains("Peluche - 5.00$");
    }

    @Test
    @Order(19)
    void advance_edition_to_post_sale_phase() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("SALE"));
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("POST_SALE"));
    }

    @Test
    @Order(20)
    void reprint_labels_in_post_sale_phase_is_blocked() throws Exception {
        // Story 5.8: labels reprinting now requires the Deposit phase, exactly like the deposit
        // slip. First real assertion of this phase gate — before story 5.8 the labels reprint was
        // allowed through Post-vente. The unavailable thermal printer is still selected in the
        // session (Order 12), but requireDepositPhaseForReprint rejects before any printer check.
        mockMvc.perform(post("/api/sellers/" + sellerAId + "/deposit/labels/reprint")
                        .session(volunteerSession).with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/deposit-reprint-not-allowed")));
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

    private Long createItem(Long sellerId, String name, String price, boolean incomplete, String comment) throws Exception {
        CreateItemDto payload = new CreateItemDto(sellerId, categoryId, name, new BigDecimal(price), incomplete, comment);
        MvcResult result = mockMvc.perform(post("/api/items")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), ItemDto.class).id();
    }
}
