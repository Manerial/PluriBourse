package org.pluribourse.domain.print.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.pluribourse.domain.print.entity.Printer;
import org.pluribourse.domain.print.entity.PrinterType;
import org.pluribourse.domain.print.exception.PrinterNotFoundException;
import org.pluribourse.domain.print.repository.PrinterRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sole entry point for submitting print jobs (ARCH-009) — one queue and one consumer thread per
 * registered printer, so a stalled/erroring printer never blocks another (FR-029).
 */
@Slf4j
@Component
public class PrintQueueService {

    private final PrinterRepository printerRepository;
    private final Map<PrinterType, PrinterConnectivityChecker> connectivityCheckersByType;
    private final Map<Long, PrinterQueueHandle> handles = new ConcurrentHashMap<>();

    public PrintQueueService(PrinterRepository printerRepository, List<PrinterConnectivityChecker> connectivityCheckers) {
        this.printerRepository = printerRepository;
        this.connectivityCheckersByType = connectivityCheckers.stream()
                .collect(Collectors.toMap(PrinterConnectivityChecker::getSupportedType, Function.identity()));
    }

    @PostConstruct
    void init() {
        reloadFromDatabase();
    }

    /**
     * (Re)registers every printer currently in the database. Called both at application startup
     * and by tests to simulate a cold restart without relaunching the JVM — idempotent, since an
     * already-registered printer id is left untouched.
     */
    public void reloadFromDatabase() {
        printerRepository.findAll().forEach(this::registerPrinter);
    }

    /**
     * Instantiates the queue and consumer thread for a printer, testing accessibility first
     * without letting an inaccessible printer block startup or its own registration (FR-079).
     */
    public void registerPrinter(Printer printer) {
        // Built outside computeIfAbsent on purpose: createHandle() performs blocking connectivity
        // I/O, and ConcurrentHashMap's remapping functions must stay quick and must not be used to
        // do long-running work while holding the map's internal bin lock.
        if (handles.containsKey(printer.getId())) {
            return;
        }
        handles.putIfAbsent(printer.getId(), createHandle(printer));
    }

    public void submit(Long printerId, PrintJob job) {
        PrinterQueueHandle handle = handles.get(printerId);
        if (handle == null) {
            throw new PrinterNotFoundException(printerId);
        }
        handle.submit(job);
    }

    /**
     * Exposed for tests and as the foundation for story 3.7's diagnostic view — not exposed via
     * HTTP by this story.
     */
    public PrinterQueueHandle getHandle(Long printerId) {
        return handles.get(printerId);
    }

    /**
     * Removes the handle from the registry and stops its consumer thread (story 3.8, AC4/AC5) —
     * idempotent, a no-op when the id is already absent.
     */
    public void unregisterPrinter(Long id) {
        PrinterQueueHandle handle = handles.remove(id);
        if (handle != null) {
            handle.stop();
        }
    }

    /**
     * True when a printer can accept a job right now — registered, not suspended, no recorded
     * error. Shared by {@link PrinterSelectionService} (selection-time check, story 3.9) and the
     * deposit-validation flow (submission-time check, story 3.5) so the definition of "available"
     * lives in one place.
     */
    public boolean isAvailable(Long printerId) {
        PrinterQueueHandle handle = handles.get(printerId);
        return handle != null && !handle.isSuspended() && handle.getLastError() == null;
    }

    /**
     * Re-runs the live connectivity check against every registered printer (FR-079 refresh
     * action) — the one-time check at {@link #registerPrinter} time goes stale as soon as a
     * printer that never has a job submitted to it comes back online or drops offline; nothing
     * else updates {@code lastError} for it in the meantime. Suspended handles are skipped: their
     * {@code lastError}/{@code suspended} pair is owned exclusively by job execution and admin
     * resume/discard (see {@link PrinterQueueHandle} Javadoc on the torn-state invariant), never
     * by a plain connectivity check.
     */
    public void refreshConnectivity() {
        printerRepository.findAll().forEach(printer -> {
            PrinterQueueHandle handle = handles.get(printer.getId());
            if (handle == null || handle.isSuspended()) {
                return;
            }
            PrinterConnectivityChecker checker = connectivityCheckersByType.get(printer.getType());
            try {
                checker.checkAccessibility(printer);
                handle.setLastError(null);
            } catch (RuntimeException e) {
                String error = PrinterQueueHandle.describeError(e);
                handle.setLastError(error);
                log.warn("Printer {} is not accessible: {}", printer.getId(), error);
            }
        });
    }

    private PrinterQueueHandle createHandle(Printer printer) {
        PrinterQueueHandle handle = new PrinterQueueHandle(printer);
        PrinterConnectivityChecker checker = connectivityCheckersByType.get(printer.getType());
        try {
            checker.checkAccessibility(printer);
        } catch (RuntimeException e) {
            String error = PrinterQueueHandle.describeError(e);
            handle.setLastError(error);
            log.warn("Printer {} is not accessible: {}", printer.getId(), error);
        }
        handle.start();
        return handle;
    }
}
