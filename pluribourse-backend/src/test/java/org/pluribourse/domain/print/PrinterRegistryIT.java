package org.pluribourse.domain.print;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.pluribourse.domain.print.dto.*;
import org.pluribourse.domain.print.entity.*;
import org.pluribourse.domain.print.exception.*;
import org.pluribourse.domain.print.service.*;
import org.pluribourse.shared.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.*;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * E2E for story 3.8 (registry CRUD), story 3.11 (PrinterBridge integration — discovery, status,
 * test print) and story 3.13 (ignoring a detected-but-unregistered printer). See
 * {@link PrinterBridgeDouble} for the fake PrinterBridge process backing
 * {@code printerbridge.base-url} for this whole class.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PrinterRegistryIT extends IntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PrintQueueService printQueueService;

    private static PrinterBridgeDouble printerBridgeDouble;

    private MockHttpSession adminSession;
    private MockHttpSession volunteerSession;
    private Long pendingVerificationPrinterId;

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

        MvcResult volunteerLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "volunteer1")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andReturn();
        volunteerSession = (MockHttpSession) volunteerLogin.getRequest().getSession(false);
    }

    @Test
    @Order(1)
    void listing_is_empty_when_no_printer_is_registered_yet() throws Exception {
        mockMvc.perform(get("/api/admin/printers").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @Order(2)
    void discovered_printers_are_listed_from_the_printerbridge_double() throws Exception {
        printerBridgeDouble.register("bridge-online-1", "Imprimante Bureau", "NETWORK", "ONLINE");

        mockMvc.perform(get("/api/admin/printers/discovered").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.printerBridgeId == 'bridge-online-1')].name").value("Imprimante Bureau"))
                .andExpect(jsonPath("$[?(@.printerBridgeId == 'bridge-online-1')].type").value("A4"))
                .andExpect(jsonPath("$[?(@.printerBridgeId == 'bridge-online-1')].status").value("ONLINE"));
    }

    @Test
    @Order(3)
    void an_already_registered_printer_is_excluded_from_discovery() throws Exception {
        printerBridgeDouble.register("bridge-already-registered-1", "Imprimante Deja Enregistree", "NETWORK", "ONLINE");
        createPrinter("Imprimante Deja Enregistree", "bridge-already-registered-1", PrinterStatus.ONLINE);

        mockMvc.perform(get("/api/admin/printers/discovered").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.printerBridgeId == 'bridge-already-registered-1')]").isEmpty());
    }

    @Test
    @Order(4)
    void reachable_printer_appears_connected_in_the_registry() throws Exception {
        printerBridgeDouble.register("bridge-online-2", "Imprimante Joignable", "NETWORK", "ONLINE");
        Long printerId = createPrinter("Imprimante Joignable", "bridge-online-2", PrinterStatus.ONLINE);

        PrinterSummaryDto summary = findSummary(printerId);
        assertThat(summary.name()).isEqualTo("Imprimante Joignable");
        assertThat(summary.type()).isEqualTo(PrinterType.A4);
        assertThat(summary.connected()).isTrue();
    }

    @Test
    @Order(5)
    void unreachable_printer_appears_disconnected_in_the_registry() throws Exception {
        // Story 3.15: creation no longer performs a live check — depopulating the double for this
        // bridgeId before the POST proves the OFFLINE status comes from the payload's seeded
        // status, not from a leftover live call.
        printerBridgeDouble.register("bridge-offline-1", "Imprimante Injoignable", "NETWORK", "OFFLINE");
        printerBridgeDouble.unregister("bridge-offline-1");
        Long printerId = createPrinter("Imprimante Injoignable", "bridge-offline-1", PrinterStatus.OFFLINE);

        PrinterSummaryDto summary = findSummary(printerId);
        assertThat(summary.connected()).isFalse();
    }

    @Test
    @Order(6)
    void test_print_relays_the_printerbridge_double_result() throws Exception {
        printerBridgeDouble.register("bridge-online-3", "Imprimante Test", "NETWORK", "ONLINE");
        Long printerId = createPrinter("Imprimante Test", "bridge-online-3", PrinterStatus.ONLINE);

        mockMvc.perform(post("/api/admin/printers/" + printerId + "/test-print")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));
    }

    @Test
    @Order(7)
    void test_print_returns_404_for_an_unknown_pluribourse_id() throws Exception {
        mockMvc.perform(post("/api/admin/printers/999999/test-print")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/printer-not-found")));
    }

    @Test
    @Order(8)
    void test_print_returns_an_error_result_when_printerbridge_no_longer_knows_the_printer() throws Exception {
        // Valid PluriBourse id, but the printerBridgeId it was registered with is no longer known
        // to PrinterBridge (e.g. a reassigned Bluetooth COM port, AC9) — PrinterBridge itself
        // answers this with a 404 on POST /test-print, which must surface as a normal ERROR
        // PrintResult, not an uncaught exception (code review finding, story 3.11/3.12).
        printerBridgeDouble.register("bridge-stale-1", "Imprimante Bientot Perimee", "NETWORK", "ONLINE");
        Long printerId = createPrinter("Imprimante Bientot Perimee", "bridge-stale-1", PrinterStatus.ONLINE);
        printerBridgeDouble.unregister("bridge-stale-1");

        mockMvc.perform(post("/api/admin/printers/" + printerId + "/test-print")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    @Test
    @Order(9)
    void deleting_a_printer_removes_it_from_the_registry_and_tears_down_its_queue() throws Exception {
        printerBridgeDouble.register("bridge-online-4", "Imprimante A Supprimer", "NETWORK", "ONLINE");
        Long printerId = createPrinter("Imprimante A Supprimer", "bridge-online-4", PrinterStatus.ONLINE);
        assertThat(printQueueService.getHandle(printerId)).isNotNull();

        mockMvc.perform(delete("/api/admin/printers/" + printerId)
                        .session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/printers").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + printerId + ")]").isEmpty());
        assertThat(printQueueService.getHandle(printerId)).isNull();
    }

    @Test
    @Order(10)
    void submitting_to_a_deleted_printer_throws_not_found() throws Exception {
        printerBridgeDouble.register("bridge-online-5", "Imprimante A Supprimer Puis Soumettre", "NETWORK", "ONLINE");
        Long printerId = createPrinter("Imprimante A Supprimer Puis Soumettre", "bridge-online-5", PrinterStatus.ONLINE);
        mockMvc.perform(delete("/api/admin/printers/" + printerId)
                        .session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());

        assertThatThrownBy(() -> printQueueService.submit(printerId, printer -> {
        }))
                .isInstanceOf(PrinterNotFoundException.class);
    }

    @Test
    @Order(11)
    void deleting_an_unknown_printer_returns_404() throws Exception {
        mockMvc.perform(delete("/api/admin/printers/999999")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/printer-not-found")));
    }

    @Test
    @Order(12)
    void volunteer_session_is_forbidden_on_every_registry_endpoint() throws Exception {
        mockMvc.perform(get("/api/admin/printers").session(volunteerSession))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/printers/discovered").session(volunteerSession))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/printers/1/test-print").session(volunteerSession).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/printers/1")
                        .session(volunteerSession).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/printers/discovered/some-id/ignore")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Some Printer\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/printers/ignored").session(volunteerSession))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/printers/ignored/some-id")
                        .session(volunteerSession).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(13)
    void ignoring_a_detected_printer_excludes_it_from_discovery() throws Exception {
        printerBridgeDouble.register("bridge-ignore-basic-1", "Imprimante Voisin", "NETWORK", "ONLINE");

        mockMvc.perform(post("/api/admin/printers/discovered/bridge-ignore-basic-1/ignore")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Imprimante Voisin\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/printers/discovered").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.printerBridgeId == 'bridge-ignore-basic-1')]").isEmpty());
    }

    @Test
    @Order(14)
    void ignoring_an_already_ignored_printer_is_idempotent() throws Exception {
        printerBridgeDouble.register("bridge-ignore-idempotent-1", "Imprimante Idempotente", "NETWORK", "ONLINE");

        mockMvc.perform(post("/api/admin/printers/discovered/bridge-ignore-idempotent-1/ignore")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Imprimante Idempotente\"}"))
                .andExpect(status().isNoContent());

        // Second call on the same printerBridgeId must also succeed as a no-op, not error.
        mockMvc.perform(post("/api/admin/printers/discovered/bridge-ignore-idempotent-1/ignore")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Imprimante Idempotente\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/printers/ignored").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.printerBridgeId == 'bridge-ignore-idempotent-1')]").isNotEmpty());
    }

    @Test
    @Order(15)
    void ignoring_an_already_registered_printer_is_rejected() throws Exception {
        printerBridgeDouble.register("bridge-ignore-registered-1", "Imprimante Deja Enregistree Pour Ignorer", "NETWORK", "ONLINE");
        createPrinter("Imprimante Deja Enregistree Pour Ignorer", "bridge-ignore-registered-1", PrinterStatus.ONLINE);

        mockMvc.perform(post("/api/admin/printers/discovered/bridge-ignore-registered-1/ignore")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Imprimante Deja Enregistree Pour Ignorer\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/invalid-printer-configuration")));
    }

    @Test
    @Order(16)
    void ignored_printers_are_listed_with_the_name_captured_at_ignore_time() throws Exception {
        mockMvc.perform(get("/api/admin/printers/ignored").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.printerBridgeId == 'bridge-ignore-basic-1')].name").value("Imprimante Voisin"))
                .andExpect(jsonPath("$[?(@.printerBridgeId == 'bridge-ignore-basic-1')].ignoredAt").exists());
    }

    @Test
    @Order(17)
    void reactivating_an_ignored_printer_returns_it_to_discovery() throws Exception {
        printerBridgeDouble.register("bridge-ignore-reactivate-1", "Imprimante A Reactiver", "NETWORK", "ONLINE");
        mockMvc.perform(post("/api/admin/printers/discovered/bridge-ignore-reactivate-1/ignore")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Imprimante A Reactiver\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/admin/printers/ignored/bridge-ignore-reactivate-1")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/printers/discovered").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.printerBridgeId == 'bridge-ignore-reactivate-1')]").isNotEmpty());
        mockMvc.perform(get("/api/admin/printers/ignored").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.printerBridgeId == 'bridge-ignore-reactivate-1')]").isEmpty());
    }

    @Test
    @Order(18)
    void reactivating_an_unknown_ignored_printer_returns_404() throws Exception {
        mockMvc.perform(delete("/api/admin/printers/ignored/never-ignored-xyz")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/ignored-printer-not-found")));
    }

    @Test
    @Order(19)
    void refresh_connectivity_detects_a_printer_that_went_offline_since_registration() throws Exception {
        // Registered ONLINE (seeded from discover() at creation, no live check performed at that
        // point) then flipped to OFFLINE on the double without any job ever submitted — nothing
        // else notices this until the targeted refresh (or the periodic scheduler) re-checks it
        // live (story 3.15, AC4 — migrated from the removed global refresh, see PrintQueueDiagnosticsIT).
        printerBridgeDouble.register("bridge-refresh-1", "Imprimante A Rafraichir", "NETWORK", "ONLINE");
        Long printerId = createPrinter("Imprimante A Rafraichir", "bridge-refresh-1", PrinterStatus.ONLINE);
        assertThat(findSummary(printerId).connected()).isTrue();

        printerBridgeDouble.register("bridge-refresh-1", "Imprimante A Rafraichir", "NETWORK", "OFFLINE");
        assertThat(findSummary(printerId).connected()).isTrue();

        PrinterSummaryDto refreshed = refreshConnectivity(printerId);
        assertThat(refreshed.connected()).isFalse();
        assertThat(findSummary(printerId).connected()).isFalse();
    }

    @Test
    @Order(20)
    void refresh_connectivity_detects_a_printer_that_came_back_online_since_registration() throws Exception {
        printerBridgeDouble.register("bridge-refresh-2", "Imprimante Redevenue Joignable", "NETWORK", "OFFLINE");
        Long printerId = createPrinter("Imprimante Redevenue Joignable", "bridge-refresh-2", PrinterStatus.OFFLINE);
        assertThat(findSummary(printerId).connected()).isFalse();

        printerBridgeDouble.register("bridge-refresh-2", "Imprimante Redevenue Joignable", "NETWORK", "ONLINE");

        PrinterSummaryDto refreshed = refreshConnectivity(printerId);
        assertThat(refreshed.connected()).isTrue();
    }

    @Test
    @Order(21)
    void refresh_connectivity_does_not_touch_a_suspended_printer() throws Exception {
        // A suspended printer's lastError/suspended pair belongs exclusively to job execution and
        // admin resume/discard (PrinterQueueHandle's torn-state invariant) — the targeted refresh
        // must leave it alone even though the double now reports the printer back online.
        printerBridgeDouble.register("bridge-refresh-3", "Imprimante Suspendue Pour Refresh Cible", "NETWORK", "ONLINE");
        Long printerId = createPrinter("Imprimante Suspendue Pour Refresh Cible", "bridge-refresh-3", PrinterStatus.ONLINE);
        printQueueService.submit(printerId, printer -> {
            throw new RuntimeException("bourrage papier");
        });
        waitUntil(() -> printQueueService.getHandle(printerId).isSuspended());

        PrinterSummaryDto refreshed = refreshConnectivity(printerId);
        assertThat(refreshed.connected()).isFalse();
        assertThat(printQueueService.getHandle(printerId).isSuspended()).isTrue();
        assertThat(printQueueService.getHandle(printerId).getLastError()).contains("bourrage papier");
    }

    @Test
    @Order(22)
    void refresh_connectivity_returns_404_for_an_unknown_printer() throws Exception {
        mockMvc.perform(post("/api/admin/printers/999999/refresh-connectivity")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/printer-not-found")));
    }

    @Test
    @Order(23)
    void refresh_connectivity_is_forbidden_for_a_volunteer_session() throws Exception {
        mockMvc.perform(post("/api/admin/printers/1/refresh-connectivity")
                        .session(volunteerSession).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(24)
    void a_printer_seeded_from_an_unknown_status_starts_pending_verification_instead_of_confidently_connected() throws Exception {
        // discover() never individually tests Bluetooth printers — status is always UNKNOWN
        // (Dev Notes); the PrinterType is irrelevant to this behavior (registerPrinter(Printer,
        // PrinterStatus) branches only on the status), so NETWORK is used here to reuse createPrinter().
        // Registering with an UNKNOWN status must not read as confidently "connected" (code review
        // finding, story 3.15): pendingVerification distinguishes it until a real check runs.
        printerBridgeDouble.register("bridge-unknown-1", "Imprimante Statut Inconnu", "NETWORK", "UNKNOWN");
        pendingVerificationPrinterId = createPrinter("Imprimante Statut Inconnu", "bridge-unknown-1", PrinterStatus.UNKNOWN);

        PrinterSummaryDto summary = findSummary(pendingVerificationPrinterId);
        assertThat(summary.connected()).isTrue();
        assertThat(summary.pendingVerification()).isTrue();
    }

    @Test
    @Order(25)
    void refreshing_a_pending_verification_printer_resolves_the_pending_flag() throws Exception {
        // bridge-unknown-1 (Order 24) is still registered UNKNOWN on the double — a real check now
        // reports it reachable, and the refresh must clear pendingVerification either way (success
        // or failure), since the ambiguity it existed to flag has just been resolved.
        printerBridgeDouble.register("bridge-unknown-1", "Imprimante Statut Inconnu", "NETWORK", "ONLINE");

        PrinterSummaryDto refreshed = refreshConnectivity(pendingVerificationPrinterId);
        assertThat(refreshed.connected()).isTrue();
        assertThat(refreshed.pendingVerification()).isFalse();
    }

    @Test
    @Order(26)
    void printerbridge_being_unreachable_is_reported_distinctly_from_a_printer_reporting_offline() throws Exception {
        // Deliberately the last test that needs a live double — stops it for good rather than
        // restarting it, avoiding a rebind race on the same ephemeral port. @AfterAll's
        // printerBridgeDouble.stop() is a harmless no-op on an already-stopped server. Order 27
        // relies on the double staying down after this point.
        printerBridgeDouble.stop();

        mockMvc.perform(get("/api/admin/printers/discovered").session(adminSession))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/printerbridge-unavailable")));
    }

    @Test
    @Order(27)
    void listing_ignored_printers_still_works_when_printerbridge_is_unreachable() throws Exception {
        // bridge-ignore-basic-1 (Order 13) and bridge-ignore-idempotent-1 (Order 14) were never
        // reactivated — the two entries left ignored, since bridge-ignore-reactivate-1 (Order 17)
        // was reactivated. Their names were captured at ignore time, before PrinterBridge went
        // down in Order 26, and are still correctly returned here — proof that listIgnored()
        // never calls PrinterBridge itself.
        mockMvc.perform(get("/api/admin/printers/ignored").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[?(@.printerBridgeId == 'bridge-ignore-basic-1')].name").value("Imprimante Voisin"))
                .andExpect(jsonPath("$[?(@.printerBridgeId == 'bridge-ignore-idempotent-1')].name").value("Imprimante Idempotente"));
    }

    @Test
    @Order(28)
    void concurrent_ignore_calls_on_the_same_printer_both_succeed() throws Exception {
        // ignore() doesn't call PrinterBridge at all — safe to run after the double is stopped
        // (Order 26). Two threads race past the pre-existing findByPrinterBridgeId().isPresent()
        // check simultaneously (both see "not yet ignored"), so the second insert hits the
        // unique constraint on printer_bridge_id — exercising the
        // catch (DataIntegrityViolationException) branch in PrinterService.ignore(), which the
        // sequential idempotency test (Order 14) never reaches.
        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            results.add(executor.submit(() -> {
                start.await();
                return mockMvc.perform(post("/api/admin/printers/discovered/bridge-ignore-concurrent-1/ignore")
                                .session(adminSession).with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Imprimante Concurrente\"}"))
                        .andReturn().getResponse().getStatus();
            }));
        }
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : results) {
            statuses.add(result.get(5, TimeUnit.SECONDS));
        }
        executor.shutdown();

        // Idempotent by design (AC1) — both concurrent calls succeed, neither surfaces the raw
        // DataIntegrityViolationException as a 500.
        assertThat(statuses).containsExactly(204, 204);
        mockMvc.perform(get("/api/admin/printers/ignored").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.printerBridgeId == 'bridge-ignore-concurrent-1')]").isNotEmpty());
    }

    private Long createPrinter(String name, String printerBridgeId, PrinterStatus status) throws Exception {
        CreatePrinterDto payload = new CreatePrinterDto(name, PrinterType.A4, null, printerBridgeId, status);
        MvcResult result = mockMvc.perform(post("/api/admin/printers")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), PrinterDto.class).id();
    }

    private PrinterSummaryDto findSummary(Long printerId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/admin/printers").session(adminSession))
                .andExpect(status().isOk())
                .andReturn();
        List<PrinterSummaryDto> summaries = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, PrinterSummaryDto.class));
        return summaries.stream()
                .filter(s -> s.id().equals(printerId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Printer not found in listing: " + printerId));
    }

    private PrinterSummaryDto refreshConnectivity(Long printerId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/printers/" + printerId + "/refresh-connectivity")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), PrinterSummaryDto.class);
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
