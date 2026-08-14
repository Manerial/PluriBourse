package org.pluribourse.domain.print;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.pluribourse.domain.print.dto.*;
import org.pluribourse.domain.print.entity.*;
import org.pluribourse.shared.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.*;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;

import java.io.IOException;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Story 3.9 Dev Notes § Stratégie de test: never add a printer to test-data.sql (would trigger
 * real hardware/network access at every IT class startup) — build a reachable and an unreachable
 * A4 printer here instead, exactly like {@link PrintInfrastructureIT}. No THERMAL printer is
 * created (jSerialComm not testable without hardware, gap already accepted in story 3.4); the
 * "wrong type" branch is exercised using the available A4 printer as a thermalPrinterId instead.
 * Since story 3.11, reachability is decided by {@link PrinterBridgeDouble}, not a raw socket.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PrinterSelectionIT extends IntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private static PrinterBridgeDouble printerBridgeDouble;

    private MockHttpSession adminSession;
    private MockHttpSession volunteerSession;

    private Long availableA4PrinterId;
    private Long unavailableA4PrinterId;

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
    void available_printers_is_empty_when_none_registered() throws Exception {
        mockMvc.perform(get("/api/printers/available").session(volunteerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @Order(2)
    void register_available_and_unavailable_a4_printers() throws Exception {
        printerBridgeDouble.register("bridge-available", "Fake Available", "NETWORK", "ONLINE");
        printerBridgeDouble.register("bridge-unavailable", "Fake Unavailable", "NETWORK", "OFFLINE");

        MvcResult available = mockMvc.perform(post("/api/admin/printers")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePrinterDto(
                                "Imprimante Disponible", PrinterType.A4, null, "bridge-available"))))
                .andExpect(status().isCreated())
                .andReturn();
        availableA4PrinterId = objectMapper.readValue(available.getResponse().getContentAsString(), PrinterDto.class).id();

        MvcResult unavailable = mockMvc.perform(post("/api/admin/printers")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePrinterDto(
                                "Imprimante Indisponible", PrinterType.A4, null, "bridge-unavailable"))))
                .andExpect(status().isCreated())
                .andReturn();
        unavailableA4PrinterId = objectMapper.readValue(unavailable.getResponse().getContentAsString(), PrinterDto.class).id();
    }

    @Test
    @Order(3)
    void available_printers_lists_only_the_reachable_one() throws Exception {
        mockMvc.perform(get("/api/printers/available").session(volunteerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(availableA4PrinterId))
                .andExpect(jsonPath("$[0].name").value("Imprimante Disponible"))
                .andExpect(jsonPath("$[0].type").value("A4"));
    }

    @Test
    @Order(4)
    void select_unknown_printer_id_returns_404() throws Exception {
        mockMvc.perform(post("/api/printers/selection")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"thermalPrinterId\":null,\"a4PrinterId\":999999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(endsWith("/printer-not-found")));
    }

    @Test
    @Order(5)
    void select_printer_with_inconsistent_type_returns_422() throws Exception {
        mockMvc.perform(post("/api/printers/selection")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"thermalPrinterId\":" + availableA4PrinterId + ",\"a4PrinterId\":null}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(endsWith("/invalid-printer-selection")));
    }

    @Test
    @Order(6)
    void select_unavailable_printer_returns_422() throws Exception {
        mockMvc.perform(post("/api/printers/selection")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"thermalPrinterId\":null,\"a4PrinterId\":" + unavailableA4PrinterId + "}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(endsWith("/invalid-printer-selection")));
    }

    @Test
    @Order(7)
    void select_valid_printer_succeeds_and_marks_done() throws Exception {
        mockMvc.perform(post("/api/printers/selection")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"thermalPrinterId\":null,\"a4PrinterId\":" + availableA4PrinterId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.done").value(true))
                .andExpect(jsonPath("$.thermalPrinterId").doesNotExist())
                .andExpect(jsonPath("$.a4PrinterId").value(availableA4PrinterId));
    }

    @Test
    @Order(8)
    void get_selection_reflects_state_after_post_on_the_same_session() throws Exception {
        mockMvc.perform(get("/api/printers/selection").session(volunteerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.done").value(true))
                .andExpect(jsonPath("$.thermalPrinterId").doesNotExist())
                .andExpect(jsonPath("$.a4PrinterId").value(availableA4PrinterId));
    }

    @Test
    @Order(9)
    void select_with_both_ids_null_still_succeeds() throws Exception {
        MvcResult volunteer2Login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "volunteer2")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession volunteer2Session = (MockHttpSession) volunteer2Login.getRequest().getSession(false);

        mockMvc.perform(post("/api/printers/selection")
                        .session(volunteer2Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"thermalPrinterId\":null,\"a4PrinterId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.done").value(true))
                .andExpect(jsonPath("$.thermalPrinterId").doesNotExist())
                .andExpect(jsonPath("$.a4PrinterId").doesNotExist());
    }

    @Test
    @Order(10)
        // Story 5.2 (AC 5): an admin can now select an A4 printer too, so they can print a
        // seller's sales report from /admin/settlement — same interstitial as the volunteer.
    void admin_session_can_reach_all_endpoints() throws Exception {
        mockMvc.perform(get("/api/printers/available").session(adminSession))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/printers/selection").session(adminSession))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/printers/selection")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"thermalPrinterId\":null,\"a4PrinterId\":" + availableA4PrinterId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.a4PrinterId").value(availableA4PrinterId));
    }

    @Test
    @Order(11)
    void select_with_non_positive_id_returns_400() throws Exception {
        mockMvc.perform(post("/api/printers/selection")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"thermalPrinterId\":0,\"a4PrinterId\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("/validation-failed")));
    }
}
