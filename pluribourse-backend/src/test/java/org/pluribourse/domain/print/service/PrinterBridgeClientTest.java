package org.pluribourse.domain.print.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.pluribourse.domain.print.entity.PrintContentType;
import org.pluribourse.domain.print.entity.PrinterStatus;
import org.pluribourse.domain.print.exception.PrinterBridgeUnavailableException;
import org.pluribourse.shared.PrinterBridgeDouble;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit-level (no Spring context) coverage of {@link PrinterBridgeClient}'s HTTP methods against a
 * real {@link PrinterBridgeDouble}, plus the connection-failure path for all four methods
 * (including {@link PrinterBridgeClient#print}, which the double cannot exercise successfully —
 * it is HTTP-only, no real WebSocket handshake — see {@code DepositSlipPrintingIT} for the
 * mocked-client coverage of a successful send, and story 3.12 Dev Notes for why a full WS-capable
 * double was descoped).
 */
class PrinterBridgeClientTest {

    private static PrinterBridgeDouble printerBridgeDouble;
    private static PrinterBridgeClient client;
    private static PrinterBridgeClient unreachableClient;

    @BeforeAll
    static void startDouble() throws IOException {
        printerBridgeDouble = PrinterBridgeDouble.start();
        client = new PrinterBridgeClient(printerBridgeDouble.baseUrl(), new ObjectMapper());
        // Port 1 refuses connections on localhost — same "nothing listening" convention already
        // used throughout this module's other tests (e.g. NetworkPrinterConnectivityChecker IT
        // coverage) for an unreachable target.
        unreachableClient = new PrinterBridgeClient("http://127.0.0.1:1", new ObjectMapper());
    }

    @AfterAll
    static void stopDouble() {
        printerBridgeDouble.stop();
    }

    @Test
    void discover_returns_the_printers_registered_on_the_double() {
        printerBridgeDouble.register("bridge-a", "Imprimante A", "NETWORK", "ONLINE");
        printerBridgeDouble.register("bridge-b", "Imprimante B", "BLUETOOTH_THERMAL", "OFFLINE");

        List<PrinterBridgeDiscoveredPrinter> printers = client.discover();

        assertThat(printers).extracting(PrinterBridgeDiscoveredPrinter::id).contains("bridge-a", "bridge-b");
        PrinterBridgeDiscoveredPrinter a = printers.stream().filter(p -> p.id().equals("bridge-a")).findFirst().orElseThrow();
        assertThat(a.name()).isEqualTo("Imprimante A");
        assertThat(a.type()).isEqualTo(PrinterBridgePrinterType.NETWORK);
        assertThat(a.status()).isEqualTo(PrinterStatus.ONLINE);

        printerBridgeDouble.unregister("bridge-a");
        printerBridgeDouble.unregister("bridge-b");
    }

    @Test
    void check_status_returns_the_registered_status() {
        printerBridgeDouble.register("bridge-status", "Imprimante Statut", "NETWORK", "OFFLINE");

        PrinterBridgeDiscoveredPrinter status = client.checkStatus("bridge-status");

        assertThat(status.status()).isEqualTo(PrinterStatus.OFFLINE);
        printerBridgeDouble.unregister("bridge-status");
    }

    @Test
    void check_status_translates_a_404_into_an_offline_status_rather_than_throwing() {
        PrinterBridgeDiscoveredPrinter status = client.checkStatus("bridge-never-registered");

        assertThat(status.status()).isEqualTo(PrinterStatus.OFFLINE);
    }

    @Test
    void test_print_relays_the_doubles_result() {
        printerBridgeDouble.register("bridge-test-print", "Imprimante Test", "NETWORK", "ONLINE");

        PrintResult result = client.testPrint("bridge-test-print");

        assertThat(result.status()).isEqualTo(PrintResultStatus.OK);
        printerBridgeDouble.unregister("bridge-test-print");
    }

    @Test
    void test_print_returns_an_error_result_instead_of_throwing_when_printerbridge_returns_a_404() {
        PrintResult result = client.testPrint("bridge-never-registered");

        assertThat(result.status()).isEqualTo(PrintResultStatus.ERROR);
    }

    @Test
    void discover_throws_printer_bridge_unavailable_when_nothing_is_listening() {
        assertThatThrownBy(unreachableClient::discover)
                .isInstanceOf(PrinterBridgeUnavailableException.class);
    }

    @Test
    void check_status_throws_printer_bridge_unavailable_when_nothing_is_listening() {
        assertThatThrownBy(() -> unreachableClient.checkStatus("any-id"))
                .isInstanceOf(PrinterBridgeUnavailableException.class);
    }

    @Test
    void print_throws_printer_bridge_unavailable_when_nothing_is_listening() {
        assertThatThrownBy(() -> unreachableClient.print("any-id", PrintContentType.ESC_POS, new byte[]{1, 2, 3}))
                .isInstanceOf(PrinterBridgeUnavailableException.class);
    }
}
