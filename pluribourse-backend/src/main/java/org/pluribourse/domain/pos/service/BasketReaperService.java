package org.pluribourse.domain.pos.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pluribourse.domain.pos.entity.Basket;
import org.pluribourse.domain.pos.repository.BasketRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Story 4.9 (SCP 2026-09-04, review decision D2) — cancels POS baskets left on a terminal that has
 * gone silent (tab/browser closed, machine crashed, network lost) and releases their lot
 * reservations, closing the gap the story 4.8 scan-time reservation left open: those reservations
 * are otherwise cleared only by an explicit basket action, so an abandoned terminal keeps a lot
 * unsellable at <em>every</em> till until the SALE → POST_SALE transition. It is also the catch-all
 * for the FR-066 idle timeout: a session that hits the 1 h inactivity limit simply stops sending
 * heartbeats and is swept like any other dead terminal — there is no session-expiry listener.
 * <p>
 * The sweep goes through {@link BasketCancellationService#cancelBasketSilently}, one call (one
 * transaction) per basket, and emits <strong>no</strong> {@code basket-cancelled} broadcast: that
 * event is untargeted, so broadcasting it here would wipe every other cashier's in-progress basket
 * — the same reason the explicit-logout path stays silent.
 * <p>
 * The scheduled method is not transactional and catches per basket, so a basket cancelled
 * concurrently (a racing {@code validate()}, logout or phase change between the query and the
 * {@code cancelBasketSilently} call) is logged and skipped without aborting the rest of the sweep.
 * Logs carry counts only — never a basket id, user id or seller identity (NFR-007).
 * <p>
 * The first sweep is held back by {@code initialDelayString} (one reaper interval) so a restart or
 * deploy that outlasts the dead-threshold does not immediately reap every still-open terminal
 * before it has had a chance to send its next heartbeat (review decision D2).
 * <p>
 * Disabled via {@code pos.basket.reaper.enabled=false} (the test profile does this so the
 * {@code @Scheduled} trigger never fires during the ordered story-board ITs; {@code PosBasketReaperIT}
 * re-enables it for its own context and invokes {@link #reapInactiveBaskets()} directly).
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pos.basket.reaper.enabled", matchIfMissing = true)
public class BasketReaperService {

    private final BasketRepository basketRepository;
    private final BasketCancellationService basketCancellationService;

    @Value("${pos.basket.heartbeat.dead-threshold:PT3M}")
    private Duration deadThreshold;

    @Value("${pos.basket.reaper.interval:PT2M}")
    private Duration reaperInterval;

    /**
     * A single startup line so an operator can confirm the reaper is wired (it is silently absent
     * when {@code pos.basket.reaper.enabled=false}, and the only symptom of that leaking into prod
     * would otherwise be lot reservations that never clear).
     */
    @PostConstruct
    void logActivation() {
        log.info("POS basket reaper active — sweeps every {} for baskets silent longer than {}",
                reaperInterval, deadThreshold);
    }

    /**
     * Cancels every basket whose last sign of life is older than the dead-threshold. Runs on a
     * fixed delay ({@code pos.basket.reaper.interval}, default {@code PT2M}) after an initial delay
     * of one interval; also called directly from {@code PosBasketReaperIT}.
     */
    @Scheduled(fixedDelayString = "${pos.basket.reaper.interval:PT2M}", initialDelayString = "${pos.basket.reaper.interval:PT2M}")
    public void reapInactiveBaskets() {
        LocalDateTime threshold = LocalDateTime.now().minus(deadThreshold);
        List<Basket> stale = basketRepository.findStale(threshold);
        int reaped = 0;
        int failed = 0;
        for (Basket basket : stale) {
            try {
                basketCancellationService.cancelBasketSilently(basket);
                reaped++;
            } catch (RuntimeException e) {
                failed++;
                log.warn("Failed to reap an inactive POS basket", e);
            }
        }
        if (reaped > 0 || failed > 0) {
            log.info("POS basket reaper: {} basket(s) reaped, {} failed", reaped, failed);
        } else {
            log.debug("POS basket reaper found no inactive basket");
        }
    }
}
