package org.pluribourse.domain.print;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.pluribourse.domain.print.dto.CreatePrinterDto;
import org.pluribourse.domain.print.dto.PrinterDto;
import org.pluribourse.domain.print.entity.PrinterStatus;
import org.pluribourse.domain.print.entity.PrinterType;
import org.pluribourse.domain.print.service.PrintQueueService;
import org.pluribourse.domain.print.service.PrinterConnectivityRefreshService;
import org.pluribourse.domain.print.service.PrinterQueueHandle;
import org.pluribourse.shared.IntegrationTest;
import org.pluribourse.shared.PrinterBridgeDouble;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 3.15 — {@link PrinterConnectivityRefreshService} periodically re-checks every registered
 * printer against PrinterBridge, on the same footing as {@code BasketReaperService} (story 4.9): it
 * calls the already-existing, already-tested {@link PrintQueueService#refreshConnectivity()}, so
 * this class only proves the scheduler is correctly wired, not the traversal/skip logic itself.
 * <p>
 * A documented exception to the project's "E2E by the controllers" rule, on the same footing as
 * {@code PosBasketReaperIT}: the fixture (printer registration) goes through the HTTP endpoint, but
 * the scheduled sweep is invoked <strong>directly</strong> ({@code @Autowired
 * PrinterConnectivityRefreshService}) — the shared test profile disables the scheduler
 * ({@code printer.connectivity.refresh.enabled=false}), so this class turns it back on for its own
 * context (forcing a dedicated Spring context and H2 database, same reasoning as
 * {@code PosBasketReaperIT}) with a {@code PT1H} interval <em>and</em> a {@code PT1H} initial delay
 * so the scheduler never fires on its own during the run.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(properties = {
        "printer.connectivity.refresh.enabled=true",
        "printer.connectivity.refresh.interval=PT1H",
        "spring.datasource.url=jdbc:h2:mem:printer-refresh-testdb;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE"
})
class PrinterConnectivityRefreshIT extends IntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PrintQueueService printQueueService;

    @Autowired
    private PrinterConnectivityRefreshService printerConnectivityRefreshService;

    private static PrinterBridgeDouble printerBridgeDouble;

    private MockHttpSession adminSession;

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
    void setUpSession() throws Exception {
        MvcResult adminLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "test_admin")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andReturn();
        adminSession = (MockHttpSession) adminLogin.getRequest().getSession(false);
    }

    @Test
    @Order(1)
    void the_scheduled_sweep_detects_a_printer_that_went_offline_since_registration() throws Exception {
        printerBridgeDouble.register("bridge-scheduled-refresh", "Imprimante Planifiee", "NETWORK", "ONLINE");
        CreatePrinterDto payload = new CreatePrinterDto(
                "Imprimante Planifiee", PrinterType.A4, null, "bridge-scheduled-refresh", PrinterStatus.ONLINE);
        MvcResult result = mockMvc.perform(post("/api/admin/printers")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();
        Long printerId = objectMapper.readValue(result.getResponse().getContentAsString(), PrinterDto.class).id();
        assertThat(printQueueService.getHandle(printerId).getLastError()).isNull();

        printerBridgeDouble.register("bridge-scheduled-refresh", "Imprimante Planifiee", "NETWORK", "OFFLINE");
        assertThat(printQueueService.getHandle(printerId).getLastError()).isNull();

        printerConnectivityRefreshService.refreshAll();

        PrinterQueueHandle handle = printQueueService.getHandle(printerId);
        assertThat(handle.getLastError()).isNotNull();
        assertThat(handle.isSuspended()).isFalse();
    }

    @Test
    @Order(2)
    void the_scheduled_sweep_resolves_a_printer_seeded_pending_verification_from_an_unknown_status() throws Exception {
        // discover() never individually tests Bluetooth printers — status is always UNKNOWN. The
        // scheduled sweep is what eventually resolves the pendingVerification flag it seeds
        // (code review finding, story 3.15), same as the admin-triggered targeted refresh.
        printerBridgeDouble.register("bridge-scheduled-unknown", "Imprimante Statut Inconnu Planifiee", "NETWORK", "UNKNOWN");
        CreatePrinterDto payload = new CreatePrinterDto(
                "Imprimante Statut Inconnu Planifiee", PrinterType.A4, null, "bridge-scheduled-unknown", PrinterStatus.UNKNOWN);
        MvcResult result = mockMvc.perform(post("/api/admin/printers")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();
        Long printerId = objectMapper.readValue(result.getResponse().getContentAsString(), PrinterDto.class).id();
        assertThat(printQueueService.getHandle(printerId).isPendingVerification()).isTrue();

        printerBridgeDouble.register("bridge-scheduled-unknown", "Imprimante Statut Inconnu Planifiee", "NETWORK", "ONLINE");
        printerConnectivityRefreshService.refreshAll();

        assertThat(printQueueService.getHandle(printerId).isPendingVerification()).isFalse();
    }
}
