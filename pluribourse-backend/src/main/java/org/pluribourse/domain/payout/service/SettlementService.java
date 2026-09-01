package org.pluribourse.domain.payout.service;

import lombok.RequiredArgsConstructor;
import org.pluribourse.domain.edition.entity.Edition;
import org.pluribourse.domain.edition.service.EditionService;
import org.pluribourse.domain.item.entity.Item;
import org.pluribourse.domain.item.repository.ItemRepository;
import org.pluribourse.domain.item.service.ItemPricing;
import org.pluribourse.domain.item.service.PhaseGuard;
import org.pluribourse.domain.payout.dto.SettleDto;
import org.pluribourse.domain.payout.dto.SettlementDto;
import org.pluribourse.domain.payout.dto.SettlementFilter;
import org.pluribourse.domain.payout.entity.Settlement;
import org.pluribourse.domain.payout.entity.SettlementStatus;
import org.pluribourse.domain.payout.exception.InvalidSettlementAmountException;
import org.pluribourse.domain.payout.exception.SellerAlreadySettledException;
import org.pluribourse.domain.payout.repository.SettlementRepository;
import org.pluribourse.domain.seller.entity.SellerProfile;
import org.pluribourse.domain.seller.exception.SellerNotFoundException;
import org.pluribourse.domain.seller.repository.SellerRepository;
import org.pluribourse.shared.sse.SettlementUpdatedEventDto;
import org.pluribourse.shared.sse.SseEmitterRegistry;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SettlementService {

    private final SellerRepository sellerRepository;
    private final SettlementRepository settlementRepository;
    private final ItemRepository itemRepository;
    private final EditionService editionService;
    private final SseEmitterRegistry sseEmitterRegistry;

    @Transactional(readOnly = true)
    public List<SettlementDto> getSettlements() {
        Edition edition = editionService.getActiveEdition();
        PhaseGuard.requirePostSalePhase(edition);
        return getSettlementsForEdition(edition);
    }

    /**
     * Extracted from {@link #getSettlements()} (story 5.5) so {@code ReportExportService} can
     * reuse the same batched computation for the settlements CSV export without duplicating it or
     * reintroducing a per-seller N+1 scan. Does not itself apply any phase guard — callers are
     * responsible for their own (this method stays reachable in Clôturée for the export, unlike
     * {@code getSettlements()}/{@code /settlements}, which stays strictly Post-vente, FR-095).
     */
    @Transactional(readOnly = true)
    public List<SettlementDto> getSettlementsForEdition(Edition edition) {
        List<SellerProfile> sellers = sellerRepository.findAllByEditionId(edition.getId());
        Map<Long, Settlement> settlementBySellerId = settlementRepository.findAllBySellerProfileEditionId(edition.getId()).stream()
                .collect(Collectors.toMap(s -> s.getSellerProfile().getId(), s -> s));
        Map<Long, List<Item>> soldItemsBySellerId = itemRepository.findAllByEditionIdAndSoldTrue(edition.getId()).stream()
                .collect(Collectors.groupingBy(i -> i.getSellerProfile().getId()));

        return sellers.stream().map(seller -> {
            BigDecimal total = ItemPricing.computeTotal(soldItemsBySellerId.getOrDefault(seller.getId(), List.of()));
            BigDecimal amountDue = ItemPricing.computeNetPayout(total, edition.getCommissionRate());
            Settlement settlement = settlementBySellerId.get(seller.getId());
            SettlementStatus status = settlement != null ? settlement.getStatus() : SettlementStatus.UNSETTLED;
            BigDecimal amountPaid = status == SettlementStatus.SETTLED ? settlement.getAmount() : null;
            return new SettlementDto(seller.getId(), seller.getFirstName(), seller.getLastName(),
                    seller.getPhone(), seller.getEmail(), amountDue, amountPaid, status);
        }).toList();
    }

    /**
     * "Recettes de l'association" (story 5.5, edition summary report): the association retains,
     * on top of the commission already tracked separately, (a) the full amount due for every
     * "Non réclamé" seller (FR-052) and (b) the shortfall between the amount due and the amount
     * actually paid out for any seller settled below what's due (FR-051, explicitly allowed).
     * Reuses the same batched sold-items-by-seller pattern as {@link #getSettlementsForEdition}
     * rather than the private per-seller {@code computeAmountDue} — that would reintroduce the
     * N+1 scan already fixed in the story 5.1 review (NFR-001, ~100 sellers). Takes the edition's
     * sold items as a parameter (story 5.5 review) rather than re-querying them: the only caller,
     * {@code ReportService.getEditionReport}, has already loaded the identical list.
     */
    @Transactional(readOnly = true)
    public BigDecimal getAssociationRetainedTotal(Edition edition, List<Item> soldItems) {
        List<Settlement> settlements = settlementRepository.findAllBySellerProfileEditionId(edition.getId());
        Map<Long, List<Item>> soldItemsBySellerId = soldItems.stream()
                .collect(Collectors.groupingBy(i -> i.getSellerProfile().getId()));

        BigDecimal retained = BigDecimal.ZERO;
        for (Settlement settlement : settlements) {
            SellerProfile seller = settlement.getSellerProfile();
            BigDecimal total = ItemPricing.computeTotal(soldItemsBySellerId.getOrDefault(seller.getId(), List.of()));
            BigDecimal amountDue = ItemPricing.computeNetPayout(total, edition.getCommissionRate());
            BigDecimal paidToSeller = settlement.getStatus() == SettlementStatus.UNCLAIMED ? BigDecimal.ZERO : settlement.getAmount();
            retained = retained.add(amountDue.subtract(paidToSeller));
        }
        return retained.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Bulk settlement report printing (story 5.6, FR-097): resolves the sellers matching the
     * server-side filter, batched (one seller query + one grouped settlement query) like
     * {@link #getSettlementsForEdition} rather than a per-seller scan — but returns the entities
     * themselves, needed to build the {@link org.pluribourse.domain.print.service.PrintJob}s, not
     * DTOs. Does not itself apply any phase guard — same convention as
     * {@link #getSettlementsForEdition}, callers are responsible for their own. Sorted by
     * {@code sellerNumber}: {@code sellerRepository.findAllByEditionId} guarantees no order, and
     * an arbitrary order would leave the physical stack of up to ~100 printed A4 reports
     * (NFR-001) in an arbitrary order for the admin to sort through afterwards.
     */
    @Transactional(readOnly = true)
    public List<SellerProfile> getSellersMatchingFilter(Edition edition, SettlementFilter filter) {
        List<SellerProfile> sellers = sellerRepository.findAllByEditionId(edition.getId());
        Map<Long, SettlementStatus> statusBySellerId = settlementRepository.findAllBySellerProfileEditionId(edition.getId()).stream()
                .collect(Collectors.toMap(s -> s.getSellerProfile().getId(), Settlement::getStatus));
        return sellers.stream()
                .filter(seller -> filter.matches(statusBySellerId.getOrDefault(seller.getId(), SettlementStatus.UNSETTLED)))
                .sorted(Comparator.comparing(SellerProfile::getSellerNumber))
                .toList();
    }

    /**
     * Edition closure (story 2.7, FR-096): auto-marks every still-unsettled seller as Non réclamé,
     * reusing {@link #getSellersMatchingFilter} (already batched, sorted by {@code sellerNumber})
     * rather than re-deriving "unsettled" from scratch. No phase guard here — same convention as
     * {@link #getSellersMatchingFilter}, the caller ({@code EditionClosingService}) is responsible
     * for its own. The confirmation-dialog total (AC 1) is computed independently, client-side,
     * from a fresh {@code GET /api/settlements} read — this method has no caller needing its own total.
     */
    @Transactional
    public void closeAllUnsettledAsUnclaimed(Edition edition) {
        List<SellerProfile> unsettledSellers = getSellersMatchingFilter(edition, SettlementFilter.UNSETTLED);
        for (SellerProfile seller : unsettledSellers) {
            BigDecimal amountDue = computeAmountDue(seller, edition);
            persistSettlement(seller, SettlementStatus.UNCLAIMED, amountDue, amountDue);
        }
    }

    @Transactional
    public SettlementDto settle(Long sellerId, SettleDto dto) {
        Edition edition = editionService.getActiveEdition();
        PhaseGuard.requirePostSalePhase(edition);
        SellerProfile seller = requireSellerOfEdition(sellerId, edition);
        requireNotAlreadySettled(seller);

        BigDecimal amountDue = computeAmountDue(seller, edition);
        BigDecimal amount = dto.amount().setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(amountDue) > 0) {
            throw new InvalidSettlementAmountException();
        }
        SettlementDto result = persistSettlement(seller, SettlementStatus.SETTLED, amount, amountDue);
        broadcastSettlementUpdatedAfterCommit(edition, seller);
        return result;
    }

    @Transactional
    public SettlementDto markUnclaimed(Long sellerId) {
        Edition edition = editionService.getActiveEdition();
        PhaseGuard.requirePostSalePhase(edition);
        SellerProfile seller = requireSellerOfEdition(sellerId, edition);
        requireNotAlreadySettled(seller);

        BigDecimal amountDue = computeAmountDue(seller, edition);
        SettlementDto result = persistSettlement(seller, SettlementStatus.UNCLAIMED, amountDue, amountDue);
        broadcastSettlementUpdatedAfterCommit(edition, seller);
        return result;
    }

    /**
     * Defers the {@code settlement-updated} SSE broadcast until after the current transaction
     * commits, never before — same pattern as {@code EditionService.savePhaseThenSendEvent}: a
     * listener must never observe an event for a settle/markUnclaimed that ends up rolled back.
     * Registered only once {@link #persistSettlement} has returned, so a lost race (409
     * {@code seller-already-settled}), a 422 amount rejection or a 404 wrong-edition never reach
     * this point. Deliberately not called from {@link #closeAllUnsettledAsUnclaimed}: edition
     * closure already broadcasts {@code phase-changed} POST_SALE→CLOSED, after which
     * {@code GET /api/settlements} answers 422 and every terminal has already left the page
     * (AC 6, story 5.7).
     */
    private void broadcastSettlementUpdatedAfterCommit(Edition edition, SellerProfile seller) {
        SettlementUpdatedEventDto event = new SettlementUpdatedEventDto(edition.getId(), seller.getId());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sseEmitterRegistry.broadcast("settlement-updated", event);
            }
        });
    }

    /**
     * Never distinguishes "doesn't exist" from "belongs to another edition" (IDOR — same
     * rationale as PosBasketService.requireOwnedBasket). Package-private (story 5.2): reused as-is
     * by {@link SettlementReportPrintService}, same package, rather than duplicated.
     */
    SellerProfile requireSellerOfEdition(Long sellerId, Edition edition) {
        SellerProfile seller = sellerRepository.findById(sellerId)
                .orElseThrow(() -> new SellerNotFoundException(sellerId));
        if (!seller.getEdition().getId().equals(edition.getId())) {
            throw new SellerNotFoundException(sellerId);
        }
        return seller;
    }

    /**
     * The actual amount handed to a seller (story 5.2, settlement report PDF): {@code null} unless
     * the seller is {@code SETTLED} — same "nothing physically paid yet/instead" rule as
     * {@link #getSettledPayoutTotal}, applied per seller rather than summed edition-wide. Reused by
     * {@link SettlementReportPrintService} rather than duplicating the {@code Settlement} lookup.
     */
    @Transactional(readOnly = true)
    public BigDecimal getAmountPaid(Long sellerId) {
        return settlementRepository.findBySellerProfileId(sellerId)
                .filter(settlement -> settlement.getStatus() == SettlementStatus.SETTLED)
                .map(Settlement::getAmount)
                .orElse(null);
    }

    /**
     * Batched variant of {@link #getAmountPaid} for {@code printAllReports} (story 5.6, up to ~100
     * sellers, NFR-001): one grouped query instead of one per seller. A seller absent from the
     * returned map has no {@code SETTLED} settlement (never printed, per the same "no misleading
     * 0€" rule as the single-seller path).
     */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> getAmountPaidBySellerId(Edition edition) {
        return settlementRepository.findAllBySellerProfileEditionId(edition.getId()).stream()
                .filter(settlement -> settlement.getStatus() == SettlementStatus.SETTLED)
                .collect(Collectors.toMap(s -> s.getSellerProfile().getId(), Settlement::getAmount));
    }

    private void requireNotAlreadySettled(SellerProfile seller) {
        if (settlementRepository.findBySellerProfileId(seller.getId()).isPresent()) {
            throw new SellerAlreadySettledException(seller.getId());
        }
    }

    private BigDecimal computeAmountDue(SellerProfile seller, Edition edition) {
        List<Item> soldItems = itemRepository.findAllBySellerProfileIdAndSoldTrue(seller.getId());
        BigDecimal total = ItemPricing.computeTotal(soldItems);
        return ItemPricing.computeNetPayout(total, edition.getCommissionRate());
    }

    /**
     * {@code amount} is the persisted/returned amount, {@code amountDue} only feeds the returned
     * DTO. They differ by construction: for {@code settle}, {@code amount} is what the volunteer
     * actually entered (FR-051, may be less than what's due) while {@code amountDue} stays the
     * full computed due amount for display; for {@code markUnclaimed} both are the same full due
     * amount (FR-052, never a volunteer choice). Never fuse the two parameters into one.
     * <p>
     * {@code requireNotAlreadySettled} only closes the TOCTOU window on the read side — the
     * {@code uk_settlements_seller_profile} unique constraint (changelog 024) is the actual guard
     * against two concurrent calls for the same seller both passing that check; a violation here
     * means a genuine race was lost, translated to the same {@link SellerAlreadySettledException}
     * the read-side check throws (same rationale as PosBasketService's optimistic-lock handling).
     * <p>
     * Creating a {@code Settlement} is a single unique INSERT, not a read-modify-write, so that
     * constraint is a <em>hard</em> guarantee here rather than a mere safety net: two concurrent
     * settle/markUnclaimed calls for one seller can only ever resolve to exactly one row plus one
     * clean HTTP 409 — the explicit conflict NFR-008 requires, with no pessimistic lock added
     * (architecture.md § Concurrence — POS rejects that). Proven under a real two-thread race by
     * {@code SettlementConcurrencyIT}.
     */
    private SettlementDto persistSettlement(SellerProfile seller, SettlementStatus status, BigDecimal amount, BigDecimal amountDue) {
        Settlement settlement = new Settlement();
        settlement.setSellerProfile(seller);
        settlement.setStatus(status);
        settlement.setAmount(amount);
        settlement.setSettledAt(LocalDateTime.now());
        try {
            settlementRepository.saveAndFlush(settlement);
        } catch (DataIntegrityViolationException e) {
            throw new SellerAlreadySettledException(seller.getId());
        }
        BigDecimal amountPaid = status == SettlementStatus.SETTLED ? amount : null;
        return new SettlementDto(seller.getId(), seller.getFirstName(), seller.getLastName(),
                seller.getPhone(), seller.getEmail(), amountDue, amountPaid, status);
    }

    /**
     * "Total des reversements nets" (edition summary report, ReportService): the sum of what was
     * actually handed to sellers, not the theoretical {@code grossRevenue - commission} — a
     * "Non réclamé" seller's {@code Settlement.amount} equals the full amount due (kept for
     * record-keeping, see {@link #closeAllUnsettledAsUnclaimed}) but nothing was physically paid
     * out, so it contributes zero here, mirroring the {@code paidToSeller} rule already used by
     * {@link #getAssociationRetainedTotal}. A still-{@code UNSETTLED} seller (no {@link Settlement}
     * row yet) also contributes zero — nothing has been reversed to them yet.
     */
    @Transactional(readOnly = true)
    public BigDecimal getSettledPayoutTotal(Edition edition) {
        List<Settlement> settlements = settlementRepository.findAllBySellerProfileEditionId(edition.getId());
        BigDecimal total = BigDecimal.ZERO;
        for (Settlement settlement : settlements) {
            if (settlement.getStatus() == SettlementStatus.SETTLED) {
                total = total.add(settlement.getAmount());
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }
}
