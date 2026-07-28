package org.pluribourse.shared;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal PrinterBridge double for integration tests — the real PrinterBridge is a
 * separately-packaged native app (see PrinterBridge/CLAUDE.md, repo
 * github.com/Manerial/PrinterBridge), never available in CI. Exposes the three routes
 * {@code PrinterBridgeClient} calls: {@code GET /printers}, {@code GET /printers/{id}/status},
 * {@code POST /printers/{id}/test-print}. Register a fake printer with {@link #register}
 * ({@code "ONLINE"}/{@code "OFFLINE"} status) to simulate a reachable/unreachable printer; an
 * unregistered id yields a 404 from both {@code /status} and {@code /test-print}, mirroring
 * PrinterBridge's own behavior for an id it no longer knows about.
 * <p>
 * Usage — one instance per test class, started before the Spring context via
 * {@code @DynamicPropertySource} (must live in the test class itself, JUnit/Spring requirement):
 * <pre>{@code
 * private static PrinterBridgeDouble printerBridgeDouble;
 *
 * @DynamicPropertySource
 * static void printerBridgeProperties(DynamicPropertyRegistry registry) throws IOException {
 *     printerBridgeDouble = PrinterBridgeDouble.start();
 *     registry.add("printerbridge.base-url", printerBridgeDouble::baseUrl);
 * }
 *
 * @AfterAll
 * void tearDownDouble() {
 *     printerBridgeDouble.stop();
 * }
 * }</pre>
 */
public final class PrinterBridgeDouble {

    private final HttpServer server;
    private final Map<String, String[]> printers = new ConcurrentHashMap<>();

    private PrinterBridgeDouble(HttpServer server) {
        this.server = server;
    }

    public static PrinterBridgeDouble start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        PrinterBridgeDouble instance = new PrinterBridgeDouble(server);
        server.createContext("/printers", instance::handle);
        server.start();
        return instance;
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** {@code type} must be {@code "BLUETOOTH_THERMAL"} or {@code "NETWORK"}, {@code status} one of {@code "ONLINE"/"OFFLINE"/"UNKNOWN"}. */
    public void register(String printerBridgeId, String name, String type, String status) {
        printers.put(printerBridgeId, new String[]{name, type, status});
    }

    public void unregister(String printerBridgeId) {
        printers.remove(printerBridgeId);
    }

    public void stop() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        try {
            if ("GET".equals(method) && path.equals("/printers")) {
                writeJson(exchange, 200, printersJson());
            } else if ("GET".equals(method) && path.endsWith("/status")) {
                String id = path.substring("/printers/".length(), path.length() - "/status".length());
                String[] entry = printers.get(id);
                writeJson(exchange, entry == null ? 404 : 200, entry == null ? "{}" : printerJson(id, entry));
            } else if ("POST".equals(method) && path.endsWith("/test-print")) {
                String id = path.substring("/printers/".length(), path.length() - "/test-print".length());
                // Matches PrinterBridge's real behavior (verified against a live instance): an
                // unknown id is a 404, same as GET /status — not a 200 carrying an ERROR body.
                writeJson(exchange, printers.containsKey(id) ? 200 : 404,
                        printers.containsKey(id) ? "{\"status\":\"OK\",\"message\":null}" : "{}");
            } else {
                writeJson(exchange, 404, "{}");
            }
        } finally {
            exchange.close();
        }
    }

    private String printersJson() {
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (Map.Entry<String, String[]> entry : printers.entrySet()) {
            if (!first) {
                json.append(",");
            }
            json.append(printerJson(entry.getKey(), entry.getValue()));
            first = false;
        }
        return json.append("]").toString();
    }

    private static String printerJson(String id, String[] entry) {
        return "{\"id\":\"" + id + "\",\"name\":\"" + entry[0] + "\",\"type\":\"" + entry[1] + "\",\"status\":\"" + entry[2] + "\"}";
    }

    private static void writeJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
