package org.pluribourse.domain.print.service;

import lombok.RequiredArgsConstructor;
import org.pluribourse.domain.print.dto.PrinterStatusDto;
import org.pluribourse.domain.print.entity.Printer;
import org.pluribourse.domain.print.exception.PrinterNotFoundException;
import org.pluribourse.domain.print.exception.PrinterQueueNotSuspendedException;
import org.pluribourse.domain.print.repository.PrinterRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Read/mutate diagnostic view over {@link PrintQueueService}'s in-memory queue state (FR-079,
 * story 3.7) — no new queueing mechanism, only exposes what {@link PrinterQueueHandle} already
 * tracks.
 */
@Service
@RequiredArgsConstructor
public class PrintQueueDiagnosticsService {

    private final PrinterRepository printerRepository;
    private final PrintQueueService printQueueService;

    public List<PrinterStatusDto> listStatuses() {
        return printerRepository.findAll().stream()
                .map(this::toStatusDto)
                .toList();
    }

    /**
     * Live-refresh action (FR-079) — {@link #listStatuses} alone only reads the cached in-memory
     * state, which can go stale for a printer that hasn't had a job submitted to it since it was
     * last reachable/unreachable. This re-runs the actual connectivity check first.
     */
    public List<PrinterStatusDto> refreshStatuses() {
        printQueueService.refreshConnectivity();
        return listStatuses();
    }

    public void resumeQueue(Long printerId) {
        PrinterQueueHandle handle = requireHandle(printerId);
        // requeueFailedJobAtHead() checks-and-mutates atomically (synchronized) so two concurrent
        // resume/discard calls on the same printer can't both act on the same failed job.
        if (!handle.requeueFailedJobAtHead()) {
            throw new PrinterQueueNotSuspendedException(printerId);
        }
    }

    public void discardFailedJob(Long printerId) {
        PrinterQueueHandle handle = requireHandle(printerId);
        if (!handle.discardFailedJob()) {
            throw new PrinterQueueNotSuspendedException(printerId);
        }
    }

    private PrinterQueueHandle requireHandle(Long printerId) {
        PrinterQueueHandle handle = printQueueService.getHandle(printerId);
        if (handle == null) {
            throw new PrinterNotFoundException(printerId);
        }
        return handle;
    }

    private PrinterStatusDto toStatusDto(Printer printer) {
        PrinterQueueHandle handle = printQueueService.getHandle(printer.getId());
        // lastError/suspended are read together via errorSnapshot() (not two separate getters) so
        // a job failing or an admin resume/discard racing with this read can't produce a torn
        // combination such as connected=true with canRetry=true.
        PrinterQueueHandle.ErrorSnapshot errorSnapshot = handle.errorSnapshot();
        return new PrinterStatusDto(
                printer.getId(),
                printer.getName(),
                printer.getType(),
                errorSnapshot.lastError() == null,
                handle.getQueueDepth(),
                handle.isJobInProgress(),
                errorSnapshot.lastError(),
                errorSnapshot.suspended()
        );
    }
}
