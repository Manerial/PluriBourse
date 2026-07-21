package org.pluribourse.domain.print.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.pluribourse.domain.print.entity.Printer;

import java.util.concurrent.LinkedBlockingDeque;

/**
 * Per-printer queue + dedicated consumer thread. State (suspended/lastError/lastFailedJob) lives
 * only in memory — recomputed from scratch on every application start, never persisted (see
 * story 3.4 Dev Notes § Statut runtime vs persistance).
 */
@Slf4j
public class PrinterQueueHandle {

    private final Printer printer;
    private final LinkedBlockingDeque<PrintJob> deque = new LinkedBlockingDeque<>();
    private final Thread consumerThread;

    @Getter
    private volatile boolean suspended = false;

    @Getter
    private volatile String lastError;

    @Getter
    private volatile PrintJob lastFailedJob;

    public PrinterQueueHandle(Printer printer) {
        this.printer = printer;
        this.consumerThread = new Thread(this::consume, "print-queue-" + printer.getId());
        this.consumerThread.setDaemon(true);
    }

    void setLastError(String lastError) {
        this.lastError = lastError;
    }

    /**
     * Falls back to the exception's class name when the message is {@code null} (common for NPEs
     * and some IO exceptions) so a real failure is never silently recorded as no error at all.
     */
    static String describeError(Throwable t) {
        return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
    }

    public void start() {
        consumerThread.start();
    }

    public void submit(PrintJob job) {
        try {
            deque.putLast(job);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while queuing a print job", e);
        }
    }

    private void consume() {
        while (true) {
            try {
                // Suspension only stops consumption — submit() above keeps accepting jobs, which
                // wait here until a future story (3.7) resumes the queue.
                if (suspended) {
                    Thread.sleep(200);
                    continue;
                }
                PrintJob job = deque.takeFirst();
                try {
                    job.execute(printer);
                } catch (Throwable e) {
                    // Caught broadly (not just RuntimeException): this daemon consumer thread must
                    // never die, otherwise the printer's queue is unrecoverable even by a future
                    // resume (story 3.7) — a dead thread cannot be un-suspended.
                    lastError = describeError(e);
                    lastFailedJob = job;
                    suspended = true;
                    log.error("Print job failed for printer {} — queue suspended", printer.getId(), e);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
