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
        createPrinter("Imprimante Deja Enregistree", "bridge-already-registered-1");

        mockMvc.perform(get("/api/admin/printers/discovered").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.printerBridgeId == 'bridge-already-registered-1')]").isEmpty());
    }

    @Test
    @Order(4)
    void reachable_printer_appears_connected_in_the_registry() throws Exception {
        printerBridgeDouble.register("bridge-online-2", "Imprimante Joignable", "NETWORK", "ONLINE");
        Long printerId = createPrinter("Imprimante Joignable", "bridge-online-2");

        PrinterSummaryDto summary = findSummary(printerId);
        assertThat(summary.name()).isEqualTo("Imprimante Joignable");
        assertThat(summary.type()).isEqualTo(PrinterType.A4);
        assertThat(summary.connected()).isTrue();
    }

    @Test
    @Order(5)
    void unreachable_printer_appears_disconnected_in_the_registry() throws Exception {
        printerBridgeDouble.register("bridge-offline-1", "Imprimante Injoignable", "NETWORK", "OFFLINE");
        Long printerId = createPrinter("Imprimante Injoignable", "bridge-offline-1");

        PrinterSummaryDto summary = findSummary(printerId);
        assertThat(summary.connected()).isFalse();
    }

    @Test
    @Order(6)
    void test_print_relays_the_printerbridge_double_result() throws Exception {
        printerBridgeDouble.register("bridge-online-3", "Imprimante Test", "NETWORK", "ONLINE");
        Long printerId = createPrinter("Imprimante Test", "bridge-online-3");

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
        Long printerId = createPrinter("Imprimante Bientot Perimee", "bridge-stale-1");
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
        Long printerId = createPrinter("Imprimante A Supprimer", "bridge-online-4");
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
        Long printerId = createPrinter("Imprimante A Supprimer Puis Soumettre", "bridge-online-5");
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
        mockMvc.perform(post("/api/admin/printers/discovered/some-id/ignore").session(volunteerSession).with(csrf()))
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
                        .session(adminSession).with(csrf()))
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
                        .session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());

        // Second call on the same printerBridgeId must also succeed as a no-op, not error.
        mockMvc.perform(post("/api/admin/printers/discovered/bridge-ignore-idempotent-1/ignore")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/printers/ignored").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.printerBridgeId == 'bridge-ignore-idempotent-1')]").isNotEmpty());
    }

    @Test
    @Order(15)
    void ignoring_an_already_registered_printer_is_rejected() throws Exception {
        printerBridgeDouble.register("bridge-ignore-registered-1", "Imprimante Deja Enregistree Pour Ignorer", "NETWORK", "ONLINE");
        createPrinter("Imprimante Deja Enregistree Pour Ignorer", "bridge-ignore-registered-1");

        mockMvc.perform(post("/api/admin/printers/discovered/bridge-ignore-registered-1/ignore")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/invalid-printer-configuration")));
    }

    @Test
    @Order(16)
    void ignored_printers_are_listed_with_their_name_resolved_from_printerbridge() throws Exception {
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
                        .session(adminSession).with(csrf()))
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
    void printerbridge_being_unreachable_is_reported_distinctly_from_a_printer_reporting_offline() throws Exception {
        // Deliberately the last test that needs a live double — stops it for good rather than
        // restarting it, avoiding a rebind race on the same ephemeral port. @AfterAll's
        // printerBridgeDouble.stop() is a harmless no-op on an already-stopped server. Order 20
        // relies on the double staying down after this point.
        printerBridgeDouble.stop();

        mockMvc.perform(get("/api/admin/printers/discovered").session(adminSession))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/printerbridge-unavailable")));
    }

    @Test
    @Order(20)
    void listing_ignored_printers_still_works_when_printerbridge_is_unreachable() throws Exception {
        // bridge-ignore-basic-1 (Order 13) and bridge-ignore-idempotent-1 (Order 14) were never
        // reactivated — the two entries left ignored, since bridge-ignore-reactivate-1 (Order 17)
        // was reactivated.
        mockMvc.perform(get("/api/admin/printers/ignored").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[?(@.printerBridgeId == 'bridge-ignore-basic-1')].name").value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.nullValue())))
                .andExpect(jsonPath("$[?(@.printerBridgeId == 'bridge-ignore-idempotent-1')].name").value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.nullValue())));
    }

    @Test
    @Order(21)
    void concurrent_ignore_calls_on_the_same_printer_both_succeed() throws Exception {
        // ignore() doesn't call PrinterBridge at all — safe to run after the double is stopped
        // (Order 19). Two threads race past the pre-existing findByPrinterBridgeId().isPresent()
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
                                .session(adminSession).with(csrf()))
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

    private Long createPrinter(String name, String printerBridgeId) throws Exception {
        CreatePrinterDto payload = new CreatePrinterDto(name, PrinterType.A4, null, printerBridgeId);
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
}
