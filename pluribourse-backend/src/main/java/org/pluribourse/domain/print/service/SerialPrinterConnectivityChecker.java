package org.pluribourse.domain.print.service;

import com.fazecast.jSerialComm.SerialPort;
import org.pluribourse.domain.print.entity.Printer;
import org.pluribourse.domain.print.entity.PrinterType;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class SerialPrinterConnectivityChecker implements PrinterConnectivityChecker {

    private static final int OPEN_TIMEOUT_MS = 2000;

    @Override
    public PrinterType getSupportedType() {
        return PrinterType.THERMAL;
    }

    /**
     * Bounds the native {@code openPort()} call to {@link #OPEN_TIMEOUT_MS} — jSerialComm gives no
     * built-in timeout for opening a port, so a faulty driver could otherwise block startup or
     * {@code POST /admin/printers} indefinitely.
     */
    @Override
    public void checkAccessibility(Printer printer) {
        try (ExecutorService executor = Executors.newSingleThreadExecutor(this::newDaemonThread)) {
            CompletableFuture.runAsync(() -> openAndClose(printer), executor)
                    .get(OPEN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new IllegalStateException("Timed out opening serial port " + printer.getSerialPort());
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause().getMessage(), e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while opening serial port " + printer.getSerialPort());
        }
    }

    private Thread newDaemonThread(Runnable r) {
        Thread thread = new Thread(r, "serial-port-check");
        thread.setDaemon(true);
        return thread;
    }

    private void openAndClose(Printer printer) {
        SerialPort port = SerialPort.getCommPort(printer.getSerialPort());
        if (!port.openPort()) {
            throw new IllegalStateException("Cannot open serial port " + printer.getSerialPort());
        }
        port.closePort();
    }
}
