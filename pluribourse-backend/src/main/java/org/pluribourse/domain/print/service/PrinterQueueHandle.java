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

    @Getter
    private volatile boolean jobInProgress = false;

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

    /**
     * Interrupts the consumer thread so it exits {@code consume()} cleanly — a job currently
     * mid-execution (blocking synchronous I/O) is not force-killed and finishes before the thread
     * observes the interruption (story 3.8 Dev Notes § Teardown de file à la suppression).
     */
    public void stop() {
        consumerThread.interrupt();
    }

    public void submit(PrintJob job) {
        try {
            deque.putLast(job);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while queuing a print job", e);
        }
    }

    public int getQueueDepth() {
        return deque.size();
    }

    /**
     * Resumes a suspended queue by putting the failed job back at the head, so it is retried
     * before any job queued behind it (story 3.7, AC3). {@code synchronized} so the
     * suspended-check and the state mutation are atomic — otherwise two concurrent resume/discard
     * calls could both observe {@code suspended} and both act on the same failed job. Returns
     * {@code false} without side effects if the queue was not suspended.
     */
    public synchronized boolean requeueFailedJobAtHead() {
        if (!suspended) {
            return false;
        }
        PrintJob job = lastFailedJob;
        if (job != null) {
            try {
                deque.putFirst(job);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while requeuing a failed print job", e);
            }
        }
        lastFailedJob = null;
        lastError = null;
        suspended = false;
        return true;
    }

    /**
     * Resumes a suspended queue by dropping the failed job — it is never retried, and consumption
     * continues with whatever is queued behind it (story 3.7, AC4). {@code synchronized} for the
     * same atomicity reason as {@link #requeueFailedJobAtHead()}. Returns {@code false} without
     * side effects if the queue was not suspended.
     */
    public synchronized boolean discardFailedJob() {
        if (!suspended) {
            return false;
        }
        lastFailedJob = null;
        lastError = null;
        suspended = false;
        return true;
    }

    /**
     * Atomic snapshot of the fields that always change together ({@code lastError}/
     * {@code suspended}), so a diagnostic read can never observe a torn combination (e.g.
     * {@code lastError == null} together with {@code suspended == true}) if it races with a job
     * failing in {@link #consume()} or an admin resume/discard call.
     */
    public synchronized ErrorSnapshot errorSnapshot() {
        return new ErrorSnapshot(lastError, suspended);
    }

    public record ErrorSnapshot(String lastError, boolean suspended) {
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
                    jobInProgress = true;
                    job.execute(printer);
                } catch (Throwable e) {
                    // Caught broadly (not just RuntimeException): this daemon consumer thread must
                    // never die, otherwise the printer's queue is unrecoverable even by a future
                    // resume (story 3.7) — a dead thread cannot be un-suspended. Synchronized on
                    // the same monitor as requeueFailedJobAtHead()/discardFailedJob() so a
                    // concurrent admin resume/discard can't interleave with a job failing here.
                    synchronized (this) {
                        lastError = describeError(e);
                        lastFailedJob = job;
                        suspended = true;
                    }
                    log.error("Print job failed for printer {} — queue suspended", printer.getId(), e);
                } finally {
                    jobInProgress = false;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
