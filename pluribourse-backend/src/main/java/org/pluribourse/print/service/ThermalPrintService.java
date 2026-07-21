package org.pluribourse.print.service;

import com.fazecast.jSerialComm.SerialPort;
import lombok.RequiredArgsConstructor;
import org.pluribourse.item.entity.Item;
import org.pluribourse.seller.entity.SellerProfile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Builds the single {@link PrintJob} submitted when a deposit is validated (FR-028): vendor
 * separator followed by every article label, article separators in between. The serial port is
 * opened for the duration of this one job and closed afterwards — same lifecycle already
 * established by {@link SerialPrinterConnectivityChecker} in story 3.4, no connection is kept open
 * between jobs.
 */
@Component
@RequiredArgsConstructor
public class ThermalPrintService {

    private static final int PRINT_TIMEOUT_MS = 10_000;

    private final ThermalLabelRenderer renderer;

    public PrintJob buildDepositJob(SellerProfile sellerProfile, List<Item> items, Locale documentLocale) {
        String sellerFullName = sellerProfile.getFirstName() + " " + sellerProfile.getLastName();
        String editionName = sellerProfile.getEdition().getName();

        return printer -> printWithTimeout(printer.getSerialPort(), sellerFullName, editionName, items, printer.getWidthMm(), documentLocale);
    }

    /**
     * Bounds the whole open/write/close sequence to {@link #PRINT_TIMEOUT_MS} — jSerialComm gives no
     * timeout guarantee on {@code openPort()} or a blocking {@code OutputStream.write()}, so a
     * hung/disconnected printer would otherwise block the print queue's consumer thread indefinitely.
     * Same reasoning, and same bounded-async-task technique, as
     * {@link SerialPrinterConnectivityChecker#checkAccessibility}.
     */
    private void printWithTimeout(String serialPort, String sellerFullName, String editionName, List<Item> items, int printerWidthMm, Locale documentLocale) {
        try (ExecutorService executor = Executors.newSingleThreadExecutor(this::newDaemonThread)) {
            CompletableFuture.runAsync(() -> writeLabels(serialPort, sellerFullName, editionName, items, printerWidthMm, documentLocale), executor)
                    .get(PRINT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new IllegalStateException("Timed out printing to serial port " + serialPort);
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause().getMessage(), e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while printing to serial port " + serialPort);
        }
    }

    private void writeLabels(String serialPort, String sellerFullName, String editionName, List<Item> items, int printerWidthMm, Locale documentLocale) {
        SerialPort port = SerialPort.getCommPort(serialPort);
        if (!port.openPort()) {
            throw new IllegalStateException("Cannot open serial port " + serialPort);
        }
        try (OutputStream stream = port.getOutputStream()) {
            stream.write(renderer.renderSellerSeparator(sellerFullName, editionName));
            for (int i = 0; i < items.size(); i++) {
                stream.write(renderer.renderLabel(items.get(i), items, printerWidthMm, documentLocale));
                if (i < items.size() - 1) {
                    stream.write(renderer.articleSeparator());
                }
            }
            stream.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            port.closePort();
        }
    }

    private Thread newDaemonThread(Runnable r) {
        Thread thread = new Thread(r, "thermal-print-write");
        thread.setDaemon(true);
        return thread;
    }
}
