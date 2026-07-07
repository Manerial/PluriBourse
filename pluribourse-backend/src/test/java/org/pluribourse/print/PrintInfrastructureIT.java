package org.pluribourse.print;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.pluribourse.print.dto.CreatePrinterDto;
import org.pluribourse.print.dto.PrinterDto;
import org.pluribourse.print.entity.Printer;
import org.pluribourse.print.entity.PrinterType;
import org.pluribourse.print.exception.PrinterNotFoundException;
import org.pluribourse.print.repository.PrinterRepository;
import org.pluribourse.print.service.PrintQueueService;
import org.pluribourse.print.service.PrinterQueueHandle;
import org.pluribourse.shared.IntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Exception documented in story 3.4 Dev Notes § Stratégie de test: no controller yet submits a
 * real print job (stories 3.5/3.6) or reads queue status (story 3.7), so this class combines HTTP
 * calls (POST /admin/printers) with direct calls on the real, fully-wired {@link PrintQueueService}
 * bean — not an isolated service test, the Spring context is the genuine integration context.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PrintInfrastructureIT extends IntegrationTest {

    @Autowired private ObjectMapper objectMapper;
    @Autowired private PrintQueueService printQueueService;
    @Autowired private PrinterRepository printerRepository;

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

    @Test @Order(1)
    void create_printer_thermal_without_serial_port_is_rejected_with_422() throws Exception {
        CreatePrinterDto payload = new CreatePrinterDto("Thermique Invalide", PrinterType.THERMAL, null, null, null, null);
        mockMvc.perform(post("/api/admin/printers")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/invalid-printer-configuration")));
    }

    @Test @Order(2)
    void create_printer_a4_without_host_is_rejected_with_422() throws Exception {
        CreatePrinterDto payload = new CreatePrinterDto("A4 Invalide", PrinterType.A4, null, null, null, null);
        mockMvc.perform(post("/api/admin/printers")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/invalid-printer-configuration")));
    }

    @Test @Order(3)
    void create_printer_as_volunteer_is_forbidden() throws Exception {
        CreatePrinterDto payload = new CreatePrinterDto("Imprimante Benevole", PrinterType.A4, null, null, "127.0.0.1", reachableTarget.getLocalPort());
        mockMvc.perform(post("/api/admin/printers")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isForbidden());
    }

    @Test @Order(4)
    void create_printer_a4_returns_created_without_runtime_status_fields() throws Exception {
        CreatePrinterDto payload = new CreatePrinterDto("Imprimante Guichet", PrinterType.A4, null, null, "127.0.0.1", reachableTarget.getLocalPort());
        mockMvc.perform(post("/api/admin/printers")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Imprimante Guichet"))
                .andExpect(jsonPath("$.type").value("A4"))
                .andExpect(jsonPath("$.host").value("127.0.0.1"))
                .andExpect(jsonPath("$.port").value(reachableTarget.getLocalPort()))
                .andExpect(jsonPath("$.suspended").doesNotExist())
                .andExpect(jsonPath("$.lastError").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist());
    }

    @Test @Order(5)
    void create_printer_a4_without_explicit_port_defaults_to_9100() throws Exception {
        CreatePrinterDto payload = new CreatePrinterDto("Imprimante Port Defaut", PrinterType.A4, null, null, "127.0.0.1", null);
        mockMvc.perform(post("/api/admin/printers")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.port").value(9100));
    }

    @Test @Order(6)
    void create_printer_with_unreachable_target_still_succeeds_and_is_marked_in_error() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/printers")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreatePrinterDto("Imprimante Hors Ligne", PrinterType.A4, null, null, "127.0.0.1", 1))))
                .andExpect(status().isCreated())
                .andReturn();
        Long printerId = objectMapper.readValue(result.getResponse().getContentAsString(), PrinterDto.class).id();

        PrinterQueueHandle handle = printQueueService.getHandle(printerId);
        assertThat(handle).isNotNull();
        assertThat(handle.getLastError()).isNotNull();
        assertThat(handle.isSuspended()).isFalse();
    }

    @Test @Order(7)
    void jobs_submitted_to_same_printer_execute_sequentially() throws Exception {
        Long printerId = createReachablePrinter("Imprimante Sequentielle");
        List<Integer> executionOrder = Collections.synchronizedList(new ArrayList<>());

        for (int i = 1; i <= 5; i++) {
            int jobIndex = i;
            printQueueService.submit(printerId, printer -> executionOrder.add(jobIndex));
        }

        waitUntil(() -> executionOrder.size() == 5);
        assertThat(executionOrder).containsExactly(1, 2, 3, 4, 5);
    }

    @Test @Order(8)
    void jobs_on_different_printers_execute_independently() throws Exception {
        Long printerA = createReachablePrinter("Imprimante Lente A");
        Long printerB = createReachablePrinter("Imprimante Rapide B");

        CountDownLatch printerAJobStarted = new CountDownLatch(1);
        CountDownLatch printerAJobCanFinish = new CountDownLatch(1);
        List<String> completionOrder = Collections.synchronizedList(new ArrayList<>());

        printQueueService.submit(printerA, printer -> {
            printerAJobStarted.countDown();
            try {
                printerAJobCanFinish.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            completionOrder.add("A");
        });
        assertThat(printerAJobStarted.await(2, TimeUnit.SECONDS)).isTrue();

        printQueueService.submit(printerB, printer -> completionOrder.add("B"));
        waitUntil(() -> completionOrder.contains("B"));
        assertThat(completionOrder).containsExactly("B");

        printerAJobCanFinish.countDown();
        waitUntil(() -> completionOrder.size() == 2);
        assertThat(completionOrder).containsExactly("B", "A");
    }

    @Test @Order(9)
    void failed_job_suspends_its_queue_without_affecting_others() throws Exception {
        Long faultyPrinter = createReachablePrinter("Imprimante en Panne");
        Long healthyPrinter = createReachablePrinter("Imprimante Saine");

        printQueueService.submit(faultyPrinter, printer -> {
            throw new RuntimeException("bourrage papier");
        });
        waitUntil(() -> printQueueService.getHandle(faultyPrinter).isSuspended());
        assertThat(printQueueService.getHandle(faultyPrinter).getLastError()).contains("bourrage papier");

        List<String> neverRun = Collections.synchronizedList(new ArrayList<>());
        printQueueService.submit(faultyPrinter, printer -> neverRun.add("should-not-run"));

        List<String> healthyRuns = Collections.synchronizedList(new ArrayList<>());
        printQueueService.submit(healthyPrinter, printer -> healthyRuns.add("ok"));
        waitUntil(() -> !healthyRuns.isEmpty());

        assertThat(neverRun).isEmpty();
        assertThat(printQueueService.getHandle(healthyPrinter).isSuspended()).isFalse();
    }

    @Test @Order(10)
    void submit_to_unknown_printer_throws_not_found() {
        assertThatThrownBy(() -> printQueueService.submit(999999L, printer -> { }))
                .isInstanceOf(PrinterNotFoundException.class);
    }

    @Test @Order(11)
    void reload_from_database_registers_existing_printer_and_marks_it_in_error_when_unreachable() {
        Printer printer = new Printer();
        printer.setName("Imprimante Redemarrage");
        printer.setType(PrinterType.A4);
        printer.setHost("127.0.0.1");
        printer.setPort(1);
        printer = printerRepository.save(printer);

        assertThat(printQueueService.getHandle(printer.getId())).isNull();

        printQueueService.reloadFromDatabase();

        PrinterQueueHandle handle = printQueueService.getHandle(printer.getId());
        assertThat(handle).isNotNull();
        assertThat(handle.getLastError()).isNotNull();
    }

    private Long createReachablePrinter(String name) throws Exception {
        CreatePrinterDto payload = new CreatePrinterDto(name, PrinterType.A4, null, null, "127.0.0.1", reachableTarget.getLocalPort());
        MvcResult result = mockMvc.perform(post("/api/admin/printers")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), PrinterDto.class).id();
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
