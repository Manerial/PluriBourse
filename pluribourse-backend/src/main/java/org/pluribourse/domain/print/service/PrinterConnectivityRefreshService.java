package org.pluribourse.domain.print.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Story 3.15 (SCP 2026-09-11) — periodically re-checks every registered, non-suspended printer
 * against PrinterBridge, replacing the connectivity check that used to run synchronously in
 * {@code POST /admin/printers} at creation time. Model copied exactly from
 * {@link org.pluribourse.domain.pos.service.BasketReaperService}: this scheduler introduces no
 * traversal/skip logic of its own, it only calls the already-existing, already-tested
 * {@link PrintQueueService#refreshConnectivity()} on a timer (AC2/AC3).
 * <p>
 * The interval is deliberately longer than {@code pos.basket.reaper.interval}: unlike a plain
 * network ping, PrinterBridge's own connectivity check can involve a Bluetooth scan
 * ({@code PrinterLocks}/RFCOMM), which is not free to run every couple of minutes.
 * <p>
 * The first sweep is held back by {@code initialDelayString} (one refresh interval) for the same
 * reason as {@code BasketReaperService}: a restart should not immediately re-scan every printer.
 * <p>
 * Disabled via {@code printer.connectivity.refresh.enabled=false} (the test profile does this so
 * the {@code @Scheduled} trigger never fires during the ordered story-board ITs;
 * {@code PrinterConnectivityRefreshIT} re-enables it for its own context and invokes
 * {@link #refreshAll()} directly).
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "printer.connectivity.refresh.enabled", matchIfMissing = true)
public class PrinterConnectivityRefreshService {

    private final PrintQueueService printQueueService;

    @Value("${printer.connectivity.refresh.interval:PT5M}")
    private Duration refreshInterval;

    /**
     * A single startup line so an operator can confirm the scheduler is wired (it is silently
     * absent when {@code printer.connectivity.refresh.enabled=false}, and the only symptom of that
     * leaking into prod would otherwise be a printer's connectivity state going stale forever).
     */
    @PostConstruct
    void logActivation() {
        log.info("Printer connectivity refresh active — sweeps every {}", refreshInterval);
    }

    /**
     * Re-checks every registered, non-suspended printer (fixed delay
     * {@code printer.connectivity.refresh.interval}, default {@code PT5M}) after an initial delay
     * of one interval; also called directly from {@code PrinterConnectivityRefreshIT}.
     */
    @Scheduled(fixedDelayString = "${printer.connectivity.refresh.interval:PT5M}",
            initialDelayString = "${printer.connectivity.refresh.interval:PT5M}")
    public void refreshAll() {
        printQueueService.refreshConnectivity();
    }
}
