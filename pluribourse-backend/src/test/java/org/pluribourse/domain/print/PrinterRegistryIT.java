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
import org.springframework.test.web.servlet.*;

import java.net.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * E2E for story 3.8 — printer registry (list/serial-ports/delete) exposed over the queue
 * infrastructure built in story 3.4. Every printer used here is registered by this class itself
 * (never in test-data.sql), and assertions target the specific printer created by each test by id
 * rather than assuming a fixed listing size — printers registered by earlier methods in this class
 * remain visible to later ones (same isolation strategy as {@link PrintQueueDiagnosticsIT}).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PrinterRegistryIT extends IntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PrintQueueService printQueueService;

    private static ServerSocket reachableTarget;

    private MockHttpSession adminSession;
    private MockHttpSession volunteerSession;

    @BeforeAll
    void setUpSessionsAndTarget() throws Exception {
        reachableTarget = new ServerSocket(0);

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

    @AfterAll
    void tearDownTarget() throws Exception {
        reachableTarget.close();
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
    void reachable_printer_appears_connected_in_the_registry() throws Exception {
        Long printerId = createReachablePrinter("Imprimante Joignable");

        PrinterSummaryDto summary = findSummary(printerId);
        assertThat(summary.name()).isEqualTo("Imprimante Joignable");
        assertThat(summary.type()).isEqualTo(PrinterType.A4);
        assertThat(summary.connected()).isTrue();
    }

    @Test
    @Order(3)
    void unreachable_printer_appears_disconnected_in_the_registry() throws Exception {
        Long printerId = createUnreachablePrinter("Imprimante Injoignable");

        PrinterSummaryDto summary = findSummary(printerId);
        assertThat(summary.connected()).isFalse();
    }

    @Test
    @Order(4)
    void listing_serial_ports_returns_200_with_a_json_array() throws Exception {
        mockMvc.perform(get("/api/admin/printers/serial-ports").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @Order(5)
    void deleting_a_printer_removes_it_from_the_registry_and_tears_down_its_queue() throws Exception {
        Long printerId = createReachablePrinter("Imprimante A Supprimer");
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
    @Order(6)
    void submitting_to_a_deleted_printer_throws_not_found() throws Exception {
        Long printerId = createReachablePrinter("Imprimante A Supprimer Puis Soumettre");
        mockMvc.perform(delete("/api/admin/printers/" + printerId)
                        .session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());

        assertThatThrownBy(() -> printQueueService.submit(printerId, printer -> {
        }))
                .isInstanceOf(PrinterNotFoundException.class);
    }

    @Test
    @Order(7)
    void deleting_an_unknown_printer_returns_404() throws Exception {
        mockMvc.perform(delete("/api/admin/printers/999999")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/printer-not-found")));
    }

    @Test
    @Order(8)
    void volunteer_session_is_forbidden_on_every_registry_endpoint() throws Exception {
        mockMvc.perform(get("/api/admin/printers").session(volunteerSession))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/printers/serial-ports").session(volunteerSession))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/printers/1")
                        .session(volunteerSession).with(csrf()))
                .andExpect(status().isForbidden());
    }

    private Long createReachablePrinter(String name) throws Exception {
        return createPrinter(name, "127.0.0.1", reachableTarget.getLocalPort());
    }

    private Long createUnreachablePrinter(String name) throws Exception {
        return createPrinter(name, "127.0.0.1", 1);
    }

    private Long createPrinter(String name, String host, int port) throws Exception {
        CreatePrinterDto payload = new CreatePrinterDto(name, PrinterType.A4, null, null, host, port);
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
