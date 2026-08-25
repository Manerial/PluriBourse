package org.pluribourse.domain.edition;

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
import org.pluribourse.domain.payout.dto.SettleDto;
import org.pluribourse.domain.pos.dto.BasketDto;
import org.pluribourse.domain.pos.dto.ValidateBasketDto;
import org.pluribourse.domain.pos.entity.PaymentMethod;
import org.pluribourse.domain.print.dto.CreatePrinterDto;
import org.pluribourse.domain.print.dto.PrinterDto;
import org.pluribourse.domain.print.entity.PrinterType;
import org.pluribourse.domain.print.service.PrintQueueService;
import org.pluribourse.domain.print.service.PrinterQueueHandle;
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
import java.time.LocalDate;
import java.util.List;
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
 * Story 2.7 (AC 1, 2, 3, 4): edition closure — atomic auto-Non-réclamé + phase transition
 * (FR-096), then a best-effort EN+FR PDF generation decoupled from that transaction. Alice sells
 * one item (10.00€) and is settled in full before closing; Bob sells one item (20.00€) and stays
 * unsettled — closing must auto-mark him Non réclamé for his full 18.00€ due (10% commission) and
 * make it show up in the association revenue total, verified via
 * {@code GET /api/admin/reports/edition/{id}} since {@code GET /api/settlements} is no longer
 * reachable once the edition leaves Post-vente (see {@code PhaseGuard.requirePostSalePhase}).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EditionClosingIT extends IntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PrintQueueService printQueueService;

    private static PrinterBridgeDouble printerBridgeDouble;

    private static final String EDITION_NAME = "Bourse Clôture 2026";
    private static final String ALICE_BARCODE = "00010001"; // 10.00€
    private static final String BOB_BARCODE = "00020001"; // 20.00€

    private MockHttpSession adminSession;
    private MockHttpSession volunteer1Session;
    private Long editionId;
    private Long categoryId;
    private Long aliceId;
    private Long bobId;
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
    @Order(2)
    void close_while_deposit_phase_returns_422() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/close")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(endsWith("/settlement-not-allowed")));
    }

    @Test
    @Order(3)
    void create_alice_and_bob_with_items() throws Exception {
        aliceId = createSeller("Alice", "Vendeuse", "alice.cloture@email.com", "0600000001");
        bobId = createSeller("Bob", "Vendeur", "bob.cloture@email.com", "0600000002");

        createItem(aliceId, "Article Alice", "10.00");
        createItem(bobId, "Article Bob", "20.00");
    }

    @Test
    @Order(4)
    void advance_to_sale_and_sell_both_items() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("SALE"));

        sellOneItem(ALICE_BARCODE, "10.00");
        sellOneItem(BOB_BARCODE, "20.00");
    }

    @Test
    @Order(5)
    void advance_to_post_sale_phase() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("POST_SALE"));
    }

    @Test
    @Order(6)
    void settle_alice_in_full_bob_stays_unsettled() throws Exception {
        // Alice: 10.00 - 10% commission = 9.00€ due, settled in full.
        mockMvc.perform(post("/api/settlements/" + aliceId + "/settle")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SettleDto(new BigDecimal("9.00")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"));
    }

    @Test
    @Order(7)
    void close_as_non_admin_returns_403() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/close")
                        .session(volunteer1Session).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(8)
    void closing_marks_bob_unclaimed_transitions_to_closed_and_association_revenue_reflects_it() throws Exception {
        EditionSummaryReportDto before = getEditionReport();

        mockMvc.perform(post("/api/admin/editions/" + editionId + "/close")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("CLOSED"));

        EditionSummaryReportDto after = getEditionReport();

        // Bob's full due amount (20.00 - 10% commission = 18.00€) is now retained by the
        // association, on top of whatever was already retained before closing (only the commission
        // here, since Alice was already settled in full).
        assertThat(after.associationRevenueTotal().subtract(before.associationRevenueTotal()))
                .isEqualByComparingTo("18.00");
    }

    @Test
    @Order(9)
    void closing_an_already_closed_edition_returns_422_phase_already_closed() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/close")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(endsWith("/phase-already-closed")));
    }

    @Test
    @Order(10)
    void register_a4_printer_and_select_it_for_admin() throws Exception {
        printerBridgeDouble.register("bridge-closing-a4", "A4 Clôture Test", "NETWORK", "ONLINE");
        MvcResult a4Result = mockMvc.perform(post("/api/admin/printers")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePrinterDto(
                                "A4 Clôture Test", PrinterType.A4, null, "bridge-closing-a4"))))
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
    void print_closure_submits_two_jobs_one_per_locale() throws Exception {
        mockMvc.perform(post("/api/admin/reports/edition/" + editionId + "/print-closure")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());

        // The real, Spring-wired PrinterBridgeClient genuinely attempts a WebSocket handshake
        // against PrinterBridgeDouble (HTTP-only) and fails — the first of the two submitted jobs
        // suspends the queue, leaving the second (the other locale) still waiting behind it. Same
        // real-path reasoning as EditionReportPrintingIT Order 17, extended to prove two jobs (not
        // one) were queued by this endpoint.
        PrinterQueueHandle handle = printQueueService.getHandle(a4PrinterId);
        waitUntil(handle::isSuspended);
        assertThat(handle.getQueueDepth()).isEqualTo(1);
        assertThat(handle.getLastError()).isNotNull();

        mockMvc.perform(post("/api/admin/print-queue/" + a4PrinterId + "/discard")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());
        waitUntil(() -> handle.getQueueDepth() == 0);
    }

    @Test
    @Order(12)
    void print_closure_without_a_printer_selected_returns_422_and_does_not_revert_the_closure() throws Exception {
        mockMvc.perform(post("/api/printers/selection")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"thermalPrinterId\":null,\"a4PrinterId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.a4PrinterId").isEmpty());

        mockMvc.perform(post("/api/admin/reports/edition/" + editionId + "/print-closure")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(endsWith("/invalid-printer-selection")));

        // The edition closure itself (AC 3) already committed independently of this printing step —
        // a printer-selection failure here must never revert it.
        mockMvc.perform(get("/api/admin/editions/" + editionId).session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("CLOSED"));
    }

    @Test
    @Order(13)
    void rollback_after_closure_still_works() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/rollback")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("POST_SALE"));
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
        Long basketId = currentBasketId();
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
