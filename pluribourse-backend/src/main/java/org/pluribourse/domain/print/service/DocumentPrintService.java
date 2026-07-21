package org.pluribourse.domain.print.service;

import lombok.RequiredArgsConstructor;
import org.pluribourse.domain.item.entity.Item;
import org.pluribourse.domain.seller.entity.SellerProfile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Builds the single {@link PrintJob} that sends the rendered deposit slip PDF (FR-031) to an A4
 * network printer. Same bounded-timeout technique as {@link ThermalPrintService}, but over a TCP
 * socket instead of a serial port — see story 3.6 Dev Notes § "Imprimante A4/USB" for why there is
 * no USB transport in this codebase.
 */
@Component
@RequiredArgsConstructor
public class DocumentPrintService {

    private static final int PRINT_TIMEOUT_MS = 10_000;
    private static final int CONNECT_TIMEOUT_MS = 2_000;

    private final DepositSlipRenderer renderer;

    public PrintJob buildDepositSlipJob(SellerProfile sellerProfile, List<Item> items, BigDecimal commissionRate, Locale documentLocale) {
        return printer -> printWithTimeout(printer.getHost(), printer.getPort(), sellerProfile, items, commissionRate, documentLocale);
    }

    /**
     * Bounds the whole render/connect/write sequence to {@link #PRINT_TIMEOUT_MS} — a
     * hung/unreachable printer would otherwise block the print queue's consumer thread
     * indefinitely. Same reasoning as {@link ThermalPrintService#printWithTimeout}.
     */
    private void printWithTimeout(String host, Integer port, SellerProfile sellerProfile, List<Item> items, BigDecimal commissionRate, Locale documentLocale) {
        try (ExecutorService executor = Executors.newSingleThreadExecutor(this::newDaemonThread)) {
            CompletableFuture.runAsync(() -> sendDocument(host, port, sellerProfile, items, commissionRate, documentLocale), executor)
                    .get(PRINT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new IllegalStateException("Timed out printing to " + host + ":" + port);
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause().getMessage(), e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while printing to " + host + ":" + port);
        }
    }

    private void sendDocument(String host, Integer port, SellerProfile sellerProfile, List<Item> items, BigDecimal commissionRate, Locale documentLocale) {
        byte[] pdf = renderer.renderSlip(sellerProfile, items, commissionRate, documentLocale);
        // Explicit connect timeout, same bound as NetworkPrinterConnectivityChecker: a plain
        // `new Socket(host, port)` blocks on the OS-default TCP connect timeout (potentially
        // minutes on a black-holed/firewalled address), which PRINT_TIMEOUT_MS's outer
        // CompletableFuture.get() cannot interrupt — it only stops waiting, not the task itself.
        try (Socket socket = new Socket(); OutputStream stream = openStream(socket, host, port)) {
            stream.write(pdf);
            stream.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private OutputStream openStream(Socket socket, String host, Integer port) throws IOException {
        socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
        return socket.getOutputStream();
    }

    private Thread newDaemonThread(Runnable r) {
        Thread thread = new Thread(r, "document-print-write");
        thread.setDaemon(true);
        return thread;
    }
}
