package org.pluribourse.domain.print;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.pluribourse.domain.print.dto.*;
import org.pluribourse.domain.print.entity.*;
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
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * E2E for story 3.7 — diagnostic view over the queue infrastructure built in story 3.4. Every
 * printer used here is registered by this class itself (never in test-data.sql, see story 3.4 Dev
 * Notes § Stratégie de test), and assertions target the specific printer created by each test by
 * id rather than assuming a fixed listing size (see Dev Notes § Isolation des tests — printers
 * registered by earlier methods in this class remain visible to later ones). Since story 3.11,
 * reachability is decided by {@link PrinterBridgeDouble}, not a raw socket.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PrintQueueDiagnosticsIT extends IntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PrintQueueService printQueueService;

    private static PrinterBridgeDouble printerBridgeDouble;
    private static int nextFakeId = 1;

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
        mockMvc.perform(get("/api/admin/print-queue").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @Order(2)
    void reachable_printer_appears_connected_with_an_empty_queue() throws Exception {
        Long printerId = createReachablePrinter("Imprimante Joignable");

        PrinterStatusDto status = findStatus(printerId);
        assertThat(status.connected()).isTrue();
        assertThat(status.queueDepth()).isZero();
        assertThat(status.jobInProgress()).isFalse();
        assertThat(status.lastError()).isNull();
        assertThat(status.canRetry()).isFalse();
    }

    @Test
    @Order(3)
    void unreachable_printer_appears_disconnected_with_its_connectivity_error_but_not_retryable() throws Exception {
        Long printerId = createUnreachablePrinter("Imprimante Injoignable");

        PrinterStatusDto status = findStatus(printerId);
        assertThat(status.connected()).isFalse();
        assertThat(status.lastError()).isNotNull();
        assertThat(status.canRetry()).isFalse();
    }

    @Test
    @Order(4)
    void job_in_progress_is_reported_while_it_executes() throws Exception {
        Long printerId = createReachablePrinter("Imprimante En Cours");
        CountDownLatch jobStarted = new CountDownLatch(1);
        CountDownLatch jobCanFinish = new CountDownLatch(1);

        printQueueService.submit(printerId, printer -> {
            jobStarted.countDown();
            try {
                jobCanFinish.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertThat(jobStarted.await(2, TimeUnit.SECONDS)).isTrue();

        assertThat(findStatus(printerId).jobInProgress()).isTrue();

        jobCanFinish.countDown();
        waitUntil(() -> !findStatus(printerId).jobInProgress());
    }

    @Test
    @Order(5)
    void failed_job_suspends_the_queue_and_is_reported_as_retryable() throws Exception {
        Long printerId = createReachablePrinter("Imprimante En Echec");

        printQueueService.submit(printerId, printer -> {
            throw new RuntimeException("bourrage papier");
        });
        waitUntil(() -> printQueueService.getHandle(printerId).isSuspended());

        // Both wait behind the suspended queue — never executed until it is resumed or discarded.
        printQueueService.submit(printerId, printer -> {
        });
        printQueueService.submit(printerId, printer -> {
        });

        PrinterStatusDto status = findStatus(printerId);
        assertThat(status.connected()).isFalse();
        assertThat(status.canRetry()).isTrue();
        assertThat(status.lastError()).contains("bourrage papier");
        assertThat(status.queueDepth()).isEqualTo(2);
    }

    @Test
    @Order(6)
    void resume_requeues_the_failed_job_at_the_head_and_the_queue_catches_up() throws Exception {
        Long printerId = createReachablePrinter("Imprimante A Relancer");
        List<Integer> executionOrder = Collections.synchronizedList(new ArrayList<>());
        AtomicBoolean firstAttemptFailed = new AtomicBoolean(false);

        printQueueService.submit(printerId, printer -> {
            if (firstAttemptFailed.compareAndSet(false, true)) {
                throw new RuntimeException("bourrage papier");
            }
            executionOrder.add(1);
        });
        waitUntil(() -> printQueueService.getHandle(printerId).isSuspended());

        printQueueService.submit(printerId, printer -> executionOrder.add(2));
        printQueueService.submit(printerId, printer -> executionOrder.add(3));

        mockMvc.perform(post("/api/admin/print-queue/" + printerId + "/resume")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());

        waitUntil(() -> executionOrder.size() == 3);
        assertThat(executionOrder).containsExactly(1, 2, 3);
        assertThat(printQueueService.getHandle(printerId).isSuspended()).isFalse();
    }

    @Test
    @Order(7)
    void discard_drops_the_failed_job_and_the_queue_resumes_with_the_next_one() throws Exception {
        Long printerId = createReachablePrinter("Imprimante A Ignorer");
        List<String> executionOrder = Collections.synchronizedList(new ArrayList<>());

        printQueueService.submit(printerId, printer -> {
            throw new RuntimeException("bourrage papier");
        });
        waitUntil(() -> printQueueService.getHandle(printerId).isSuspended());

        printQueueService.submit(printerId, printer -> executionOrder.add("suivant"));

        mockMvc.perform(post("/api/admin/print-queue/" + printerId + "/discard")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());

        waitUntil(() -> !executionOrder.isEmpty());
        assertThat(executionOrder).containsExactly("suivant");
        assertThat(printQueueService.getHandle(printerId).isSuspended()).isFalse();
    }

    @Test
    @Order(8)
    void resume_and_discard_return_422_when_the_queue_is_not_suspended() throws Exception {
        Long printerId = createReachablePrinter("Imprimante Non Suspendue");

        mockMvc.perform(post("/api/admin/print-queue/" + printerId + "/resume")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/printer-queue-not-suspended")));

        mockMvc.perform(post("/api/admin/print-queue/" + printerId + "/discard")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/printer-queue-not-suspended")));
    }

    @Test
    @Order(9)
    void resume_and_discard_return_404_for_an_unknown_printer() throws Exception {
        mockMvc.perform(post("/api/admin/print-queue/999999/resume")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/printer-not-found")));

        mockMvc.perform(post("/api/admin/print-queue/999999/discard")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/printer-not-found")));
    }

    @Test
    @Order(10)
    void volunteer_session_is_forbidden_on_every_endpoint() throws Exception {
        mockMvc.perform(get("/api/admin/print-queue").session(volunteerSession))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/print-queue/1/resume")
                        .session(volunteerSession).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/print-queue/1/discard")
                        .session(volunteerSession).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/print-queue/refresh")
                        .session(volunteerSession).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(11)
    void concurrent_resume_requests_only_requeue_the_failed_job_once() throws Exception {
        Long printerId = createReachablePrinter("Imprimante Course Concurrente");
        List<Integer> executionOrder = Collections.synchronizedList(new ArrayList<>());
        AtomicBoolean firstAttemptFailed = new AtomicBoolean(false);

        printQueueService.submit(printerId, printer -> {
            if (firstAttemptFailed.compareAndSet(false, true)) {
                throw new RuntimeException("bourrage papier");
            }
            executionOrder.add(1);
        });
        waitUntil(() -> printQueueService.getHandle(printerId).isSuspended());

        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            results.add(executor.submit(() -> {
                start.await();
                return mockMvc.perform(post("/api/admin/print-queue/" + printerId + "/resume")
                                .session(adminSession).with(csrf()))
                        .andReturn().getResponse().getStatus();
            }));
        }
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : results) {
            statuses.add(result.get(5, TimeUnit.SECONDS));
        }
        executor.shutdown();

        // Exactly one concurrent resume wins the race (204); the other observes the
        // already-resumed queue and is correctly rejected (422) rather than double-requeuing.
        assertThat(statuses).containsExactlyInAnyOrder(204, 422);
        waitUntil(() -> executionOrder.size() == 1);
        Thread.sleep(100);
        assertThat(executionOrder).containsExactly(1);
    }

    @Test
    @Order(12)
    void refresh_detects_a_printer_that_went_offline_since_registration() throws Exception {
        // Registered ONLINE (checkAccessibility passes at registration time), then flipped to
        // OFFLINE on the double without any job ever being submitted — nothing else would ever
        // notice this. Plain listStatuses() (GET) must still show the stale cached state; only
        // the refresh action re-checks live.
        String bridgeId = "bridge-" + nextFakeId++;
        printerBridgeDouble.register(bridgeId, "Fake " + bridgeId, "NETWORK", "ONLINE");
        Long printerId = createPrinterWithBridgeId("Imprimante Devenue Injoignable", bridgeId);
        assertThat(findStatus(printerId).connected()).isTrue();

        printerBridgeDouble.register(bridgeId, "Fake " + bridgeId, "NETWORK", "OFFLINE");
        assertThat(findStatus(printerId).connected()).isTrue();

        List<PrinterStatusDto> refreshed = refreshStatuses();
        assertThat(statusFor(refreshed, printerId).connected()).isFalse();
        assertThat(findStatus(printerId).connected()).isFalse();
    }

    @Test
    @Order(13)
    void refresh_detects_a_printer_that_came_back_online_since_registration() throws Exception {
        String bridgeId = "bridge-" + nextFakeId++;
        printerBridgeDouble.register(bridgeId, "Fake " + bridgeId, "NETWORK", "OFFLINE");
        Long printerId = createPrinterWithBridgeId("Imprimante Redevenue Joignable", bridgeId);
        assertThat(findStatus(printerId).connected()).isFalse();

        printerBridgeDouble.register(bridgeId, "Fake " + bridgeId, "NETWORK", "ONLINE");

        List<PrinterStatusDto> refreshed = refreshStatuses();
        assertThat(statusFor(refreshed, printerId).connected()).isTrue();
    }

    @Test
    @Order(14)
    void refresh_does_not_touch_a_suspended_printer() throws Exception {
        // A suspended printer's lastError/suspended pair belongs exclusively to job execution and
        // admin resume/discard (PrinterQueueHandle's torn-state invariant) — refresh must leave it
        // alone even though the double now reports the printer back online.
        Long printerId = createReachablePrinter("Imprimante Suspendue Pour Refresh");
        printQueueService.submit(printerId, printer -> {
            throw new RuntimeException("bourrage papier");
        });
        waitUntil(() -> printQueueService.getHandle(printerId).isSuspended());

        List<PrinterStatusDto> refreshed = refreshStatuses();
        PrinterStatusDto status = statusFor(refreshed, printerId);
        assertThat(status.canRetry()).isTrue();
        assertThat(status.lastError()).contains("bourrage papier");
    }

    private List<PrinterStatusDto> refreshStatuses() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/print-queue/refresh")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(
                result.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, PrinterStatusDto.class));
    }

    private PrinterStatusDto statusFor(List<PrinterStatusDto> statuses, Long printerId) {
        return statuses.stream()
                .filter(s -> s.id().equals(printerId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Printer not found in listing: " + printerId));
    }

    private Long createReachablePrinter(String name) throws Exception {
        return createPrinter(name, "ONLINE");
    }

    private Long createUnreachablePrinter(String name) throws Exception {
        return createPrinter(name, "OFFLINE");
    }

    private Long createPrinter(String name, String bridgeStatus) throws Exception {
        String bridgeId = "bridge-" + nextFakeId++;
        printerBridgeDouble.register(bridgeId, "Fake " + bridgeId, "NETWORK", bridgeStatus);
        return createPrinterWithBridgeId(name, bridgeId);
    }

    private Long createPrinterWithBridgeId(String name, String bridgeId) throws Exception {
        CreatePrinterDto payload = new CreatePrinterDto(name, PrinterType.A4, null, bridgeId);
        MvcResult result = mockMvc.perform(post("/api/admin/printers")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), PrinterDto.class).id();
    }

    private PrinterStatusDto findStatus(Long printerId) {
        try {
            MvcResult result = mockMvc.perform(get("/api/admin/print-queue").session(adminSession))
                    .andExpect(status().isOk())
                    .andReturn();
            List<PrinterStatusDto> statuses = objectMapper.readValue(
                    result.getResponse().getContentAsString(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, PrinterStatusDto.class));
            return statuses.stream()
                    .filter(s -> s.id().equals(printerId))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Printer not found in listing: " + printerId));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void waitUntil(ThrowingBooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Condition not met within timeout");
    }

    @FunctionalInterface
    private interface ThrowingBooleanSupplier {
        boolean getAsBoolean();
    }
}
