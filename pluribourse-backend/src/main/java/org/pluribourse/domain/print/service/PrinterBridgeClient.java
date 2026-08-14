package org.pluribourse.domain.print.service;

import com.fasterxml.jackson.databind.*;
import org.pluribourse.domain.print.entity.*;
import org.pluribourse.domain.print.exception.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.core.*;
import org.springframework.http.client.*;
import org.springframework.stereotype.*;
import org.springframework.web.client.*;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.standard.*;
import org.springframework.web.socket.handler.*;
import org.springframework.web.util.*;

import java.io.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Sole point of contact with PrinterBridge (the native, separately-installed service that owns
 * the actual printer connections — see {@code PrinterBridge/CLAUDE.md}, repo
 * github.com/Manerial/PrinterBridge). Two timeout profiles for the HTTP calls: a short one for
 * discovery/status checks, which must never block server startup or {@code POST /admin/printers}
 * for long, and a longer one for on-demand test prints. {@link #print} (story 3.12, WebSocket) has
 * its own single bounded timeout covering the whole connect/send/await sequence.
 */
@Component
public class PrinterBridgeClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration STATUS_READ_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration PRINT_READ_TIMEOUT = Duration.ofSeconds(15);
    private static final long PRINT_WS_TIMEOUT_SECONDS = 10;

    private final RestClient statusClient;
    private final RestClient printClient;
    private final String wsBaseUrl;
    private final ObjectMapper objectMapper;
    // Shared rather than created per print() call — StandardWebSocketClient wraps a JSR-356
    // container, no reason to pay its setup cost on every job.
    private final StandardWebSocketClient webSocketClient = new StandardWebSocketClient();

    public PrinterBridgeClient(@Value("${printerbridge.base-url}") String baseUrl, ObjectMapper objectMapper) {
        this.statusClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory(STATUS_READ_TIMEOUT))
                .build();
        this.printClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory(PRINT_READ_TIMEOUT))
                .build();
        this.wsBaseUrl = baseUrl.replaceFirst("^http", "ws");
        this.objectMapper = objectMapper;
    }

    private static SimpleClientHttpRequestFactory requestFactory(Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(readTimeout);
        return factory;
    }

    public List<PrinterBridgeDiscoveredPrinter> discover() {
        try {
            return statusClient.get()
                    .uri("/printers")
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        } catch (ResourceAccessException e) {
            throw unavailable(e);
        }
    }

    /**
     * Returns the printer's live status. A 404 from PrinterBridge means the id is no longer
     * known to it (e.g. a reassigned Bluetooth COM port) — not the same failure as PrinterBridge
     * itself being unreachable, so it is translated to an {@link PrinterStatus#OFFLINE} result
     * rather than an exception (name/type are unknown at that point, left {@code null} — callers
     * only inspect {@link PrinterBridgeDiscoveredPrinter#status()}).
     */
    public PrinterBridgeDiscoveredPrinter checkStatus(String printerBridgeId) {
        try {
            return statusClient.get()
                    .uri("/printers/{id}/status", printerBridgeId)
                    .retrieve()
                    .body(PrinterBridgeDiscoveredPrinter.class);
        } catch (HttpClientErrorException.NotFound e) {
            return new PrinterBridgeDiscoveredPrinter(printerBridgeId, null, null, PrinterStatus.OFFLINE);
        } catch (ResourceAccessException e) {
            throw unavailable(e);
        }
    }

    /**
     * A 404 means the id is no longer known to PrinterBridge (e.g. a reassigned Bluetooth COM
     * port, same case as {@link #checkStatus}) — translated to an {@code ERROR} {@link
     * PrintResult} rather than an exception, consistent with how every other printer-specific
     * test-print failure (busy, dead link, ...) already surfaces from PrinterBridge itself as a
     * normal result rather than an HTTP error.
     */
    public PrintResult testPrint(String printerBridgeId) {
        try {
            return printClient.post()
                    .uri("/printers/{id}/test-print", printerBridgeId)
                    .retrieve()
                    .body(PrintResult.class);
        } catch (HttpClientErrorException.NotFound e) {
            return new PrintResult(PrintResultStatus.ERROR, "Printer no longer known to PrinterBridge: " + printerBridgeId);
        } catch (ResourceAccessException e) {
            throw unavailable(e);
        }
    }

    /**
     * Sends print content to a specific printer over {@code WS /printers/{id}/print} (story
     * 3.12): a JSON control message ({@code contentType}/{@code size}) followed by one binary
     * frame carrying {@code payload}, then blocks for PrinterBridge's {@link PrintResult}. The
     * whole sequence (connect, send, await result) runs inside one bounded async task — a single
     * timeout for the entire round-trip, same executor+{@link CompletableFuture} idiom already
     * used throughout this module's other bounded I/O calls — rather than juggling a separate
     * timeout per step.
     */
    public void print(String printerBridgeId, PrintContentType contentType, byte[] payload) {
        ExecutorService executor = Executors.newSingleThreadExecutor(PrinterBridgeClient::newDaemonThread);
        try {
            CompletableFuture.runAsync(() -> sendAndAwaitResult(printerBridgeId, contentType, payload), executor)
                    .get(PRINT_WS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new PrinterBridgeUnavailableException(
                    "Timed out printing via PrinterBridge for printer " + printerBridgeId, e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof PrinterBridgeUnavailableException pbue) {
                throw pbue;
            }
            if (e.getCause() instanceof IllegalStateException ise) {
                throw ise;
            }
            throw unavailable(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PrinterBridgeUnavailableException(
                    "Interrupted while printing via PrinterBridge for printer " + printerBridgeId, e);
        } finally {
            // shutdownNow() interrupts a worker thread still stuck in a blocking WS call (both the
            // connect step and resultFuture.get() below are interruptible) so it can never outlive
            // this method — a plain shutdown()/try-with-resources close() would instead wait for
            // that thread to finish naturally, which never happens if PrinterBridge accepts the
            // connection but never answers, silently defeating the timeout above.
            executor.shutdownNow();
        }
    }

    private void sendAndAwaitResult(String printerBridgeId, PrintContentType contentType, byte[] payload) {
        CompletableFuture<PrintResult> resultFuture = new CompletableFuture<>();
        String wsUrl = UriComponentsBuilder.fromUriString(wsBaseUrl)
                .path("/printers/{id}/print")
                .buildAndExpand(printerBridgeId)
                .encode()
                .toUriString();
        WebSocketSession session;
        try {
            session = webSocketClient.execute(new ResultCapturingHandler(resultFuture), wsUrl).get();
        } catch (Exception e) {
            // Connection-level failure (refused, handshake rejected, ...) — PrinterBridge itself
            // is the problem, not this specific print job. Must NOT be an IllegalStateException:
            // print()'s outer catch relies on the exception type alone to tell this apart from the
            // ERROR-result case below, which IS content-specific and stays an IllegalStateException.
            throw new PrinterBridgeUnavailableException("Could not open a WebSocket connection to PrinterBridge", e);
        }
        try {
            String controlJson = objectMapper.writeValueAsString(Map.of("contentType", contentType, "size", payload.length));
            session.sendMessage(new TextMessage(controlJson));
            session.sendMessage(new BinaryMessage(payload));
            PrintResult result = resultFuture.get();
            if (result.status() == PrintResultStatus.ERROR) {
                throw new IllegalStateException(result.message());
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            // Failure after a successful connection (dropped mid-send, malformed response, ...) —
            // still a connection-level problem, not a "PrinterBridge said no" content failure.
            throw new PrinterBridgeUnavailableException("Failed to send print job via PrinterBridge", e);
        } finally {
            closeQuietly(session);
        }
    }

    private static void closeQuietly(WebSocketSession session) {
        try {
            if (session.isOpen()) {
                session.close();
            }
        } catch (IOException ignored) {
            // Best-effort cleanup — the session is either already closed or about to be discarded
            // along with the interrupted worker thread regardless.
        }
    }

    /**
     * PrinterBridge never sends a binary frame back (its own {@code ApiServer.sendResult()}
     * always answers in JSON text), so a {@link TextWebSocketHandler} — which only implements
     * {@code handleTextMessage} — is sufficient.
     */
    private final class ResultCapturingHandler extends TextWebSocketHandler {

        private final CompletableFuture<PrintResult> resultFuture;

        private ResultCapturingHandler(CompletableFuture<PrintResult> resultFuture) {
            this.resultFuture = resultFuture;
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
            PrintResult result = objectMapper.readValue(message.getPayload(), PrintResult.class);
            resultFuture.complete(result);
            session.close();
        }
    }

    private static Thread newDaemonThread(Runnable r) {
        Thread thread = new Thread(r, "printerbridge-print");
        thread.setDaemon(true);
        return thread;
    }

    private PrinterBridgeUnavailableException unavailable(Exception cause) {
        return new PrinterBridgeUnavailableException("PrinterBridge service is unreachable: " + cause.getMessage(), cause);
    }
}
