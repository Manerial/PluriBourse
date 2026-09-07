package org.pluribourse.domain.pos.service;

import lombok.RequiredArgsConstructor;
import org.pluribourse.domain.edition.entity.Edition;
import org.pluribourse.domain.edition.service.EditionService;
import org.pluribourse.domain.item.entity.Item;
import org.pluribourse.domain.item.entity.Lot;
import org.pluribourse.domain.item.repository.ItemRepository;
import org.pluribourse.domain.item.repository.LotRepository;
import org.pluribourse.domain.item.service.ItemPricing;
import org.pluribourse.domain.item.service.PhaseGuard;
import org.pluribourse.domain.pos.dto.BasketDto;
import org.pluribourse.domain.pos.dto.ConflictingItemDto;
import org.pluribourse.domain.pos.dto.LotGroupDto;
import org.pluribourse.domain.pos.dto.SaleDto;
import org.pluribourse.domain.pos.dto.ScanResultDto;
import org.pluribourse.domain.pos.dto.ValidateBasketDto;
import org.pluribourse.domain.pos.entity.Basket;
import org.pluribourse.domain.pos.entity.BasketItem;
import org.pluribourse.domain.pos.entity.PaymentMethod;
import org.pluribourse.domain.pos.entity.Sale;
import org.pluribourse.domain.pos.exception.BasketItemNotFoundException;
import org.pluribourse.domain.pos.exception.BasketLotNotFoundException;
import org.pluribourse.domain.pos.exception.BasketNotFoundException;
import org.pluribourse.domain.pos.exception.BasketValidationConflictException;
import org.pluribourse.domain.pos.exception.EmptyBasketException;
import org.pluribourse.domain.pos.exception.InvalidAmountGivenException;
import org.pluribourse.domain.pos.exception.ItemAlreadyInBasketException;
import org.pluribourse.domain.pos.exception.LotAlreadySoldException;
import org.pluribourse.domain.pos.exception.LotReservedException;
import org.pluribourse.domain.pos.mapper.ScanResultMapper;
import org.pluribourse.domain.pos.repository.BasketItemRepository;
import org.pluribourse.domain.pos.repository.BasketRepository;
import org.pluribourse.domain.pos.repository.SaleRepository;
import org.pluribourse.domain.user.repositories.UserRepository;
import org.hibernate.exception.SnapshotIsolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Story 4.2 — persisted POS basket (replaces the client-only basket from story 4.1's scanner,
 * NFR-006). All five public methods require the Sale phase (AC 9): {@code addItem} inherits the
 * guard from {@link PosScanService#scan}, the other four re-check it explicitly since a basket
 * can legitimately outlive a phase change (its server-side cancellation is story 2.8, not this
 * story) and must never let a stale basket validate a payment outside the Sale phase.
 */
@Service
@RequiredArgsConstructor
public class PosBasketService {

    private final BasketRepository basketRepository;
    private final BasketItemRepository basketItemRepository;
    private final SaleRepository saleRepository;
    private final ItemRepository itemRepository;
    private final UserRepository userRepository;
    private final EditionService editionService;
    private final PosScanService posScanService;
    private final ScanResultMapper scanResultMapper;
    private final LotRepository lotRepository;
    private final BasketCancellationService basketCancellationService;

    @Transactional
    public BasketDto getOrCreateCurrentBasket(Long userId) {
        Edition edition = editionService.getActiveEdition();
        PhaseGuard.requireSalePhase(edition);
        Basket basket = basketRepository.findByEditionIdAndUserId(edition.getId(), userId)
                .orElseGet(() -> createBasket(edition, userId));
        touch(basket);
        return toDto(basket);
    }

    /**
     * FR-110 / FR-066 (SCP 2026-09-04) — records a sign of life from the cashier's POS page so
     * {@link BasketReaperService} does not sweep an actively-used basket. Sale phase is required
     * like the four mutating methods (a heartbeat outside the Sale phase means a stale page, and a
     * {@code Basket} only legitimately exists during that phase). Returns nothing: the controller
     * answers 204.
     */
    @Transactional
    public void recordHeartbeat(Long basketId, Long userId) {
        PhaseGuard.requireSalePhase(editionService.getActiveEdition());
        Basket basket = requireOwnedBasket(basketId, userId);
        touch(basket);
    }

    @Transactional
    public BasketDto addItem(Long basketId, String barcode, Long userId) {
        Basket basket = requireOwnedBasket(basketId, userId);
        // Delegates phase + format + not-found + already-sold validation to the existing scan
        // contract (story 4.1) rather than duplicating the barcode regex/parsing here.
        ScanResultDto scanned = posScanService.scan(barcode);
        if (basketItemRepository.findByBasketIdAndItemId(basketId, scanned.itemId()).isPresent()) {
            throw new ItemAlreadyInBasketException(scanned.itemId());
        }
        // FR-109 (SCP 2026-09-03): reserve the lot for this basket the first time one of its members
        // is added — a following member of the same lot re-uses the existing reservation (no write).
        if (scanned.lotId() != null
                && basketItemRepository.findAllByBasketIdAndItemLotId(basketId, scanned.lotId()).isEmpty()) {
            reserveLotForBasket(scanned.lotId(), basketId);
        }
        BasketItem basketItem = new BasketItem();
        basketItem.setBasket(basket);
        basketItem.setItem(itemRepository.getReferenceById(scanned.itemId()));
        try {
            basketItemRepository.saveAndFlush(basketItem);
        } catch (DataIntegrityViolationException e) {
            // Same race as createBasket() below (e.g. a double-tap re-sending the same scan):
            // the loser reports the clean 409 instead of letting the unique constraint violation
            // surface as a raw 500.
            throw new ItemAlreadyInBasketException(scanned.itemId());
        }
        touch(basket);
        return toDto(basket);
    }

    @Transactional
    public BasketDto removeItem(Long basketId, Long itemId, Long userId) {
        PhaseGuard.requireSalePhase(editionService.getActiveEdition());
        Basket basket = requireOwnedBasket(basketId, userId);
        BasketItem basketItem = basketItemRepository.findByBasketIdAndItemId(basketId, itemId)
                .orElseThrow(() -> new BasketItemNotFoundException(basketId, itemId));
        Long lotId = basketItem.getItem().getLot() != null ? basketItem.getItem().getLot().getId() : null;
        basketItemRepository.delete(basketItem);
        // FR-109: release the lot reservation only once its last member leaves the basket. The
        // query below auto-flushes the pending delete first (same behaviour toDto() relies on).
        if (lotId != null && basketItemRepository.findAllByBasketIdAndItemLotId(basketId, lotId).isEmpty()) {
            lotRepository.releaseLot(lotId, basketId);
        }
        touch(basket);
        return toDto(basket);
    }

    /**
     * Removes every item of the given lot currently in the basket in one call (AC 4) — not an
     * item-by-item client-side removal. Same guard order as {@code removeItem} (phase before
     * ownership, AC 9).
     */
    @Transactional
    public BasketDto removeLot(Long basketId, Long lotId, Long userId) {
        PhaseGuard.requireSalePhase(editionService.getActiveEdition());
        Basket basket = requireOwnedBasket(basketId, userId);
        List<BasketItem> lotItems = basketItemRepository.findAllByBasketIdAndItemLotId(basketId, lotId);
        if (lotItems.isEmpty()) {
            throw new BasketLotNotFoundException(basketId, lotId);
        }
        basketItemRepository.deleteAll(lotItems);
        lotRepository.releaseLot(lotId, basketId);
        touch(basket);
        return toDto(basket);
    }

    /**
     * Validates the basket's payment atomically: either every item is marked sold and the basket
     * is replaced by a {@code Sale}, or nothing is persisted at all (AC 4). Guards, in order: Sale
     * phase (AC 9), ownership (IDOR), non-empty basket, no item already sold, no lot with a sibling
     * already sold in a committed sale (FR-109 pre-check — a case the scan-time lot reservation
     * does not cover: the basket, and its reservation, may be long gone), a sufficient CASH amount,
     * then a per-item optimistic-lock check (AC 8) so that every item that actually lost a
     * concurrent-sale race on its own {@code @Version} is reported precisely — not just the first
     * one Hibernate happens to surface. The multi-terminal race on <em>different</em> members of the
     * same lot is no longer possible here: since story 4.8 a lot is reserved for a single basket the
     * moment its first member is scanned (SCP 2026-09-03), so the old force-increment of
     * {@code Lot.@Version} is gone. The reservation is released explicitly before the basket is
     * deleted.
     */
    @Transactional
    public SaleDto validate(Long basketId, ValidateBasketDto dto, Long userId) {
        Edition edition = editionService.getActiveEdition();
        PhaseGuard.requireSalePhase(edition);
        Basket basket = requireOwnedBasket(basketId, userId);

        List<Item> items = basketItemsOf(basket);
        if (items.isEmpty()) {
            throw new EmptyBasketException(basketId);
        }

        List<ConflictingItemDto> alreadySold = items.stream()
                .filter(Item::isSold)
                .map(item -> new ConflictingItemDto(item.getId(), item.getName()))
                .toList();
        if (!alreadySold.isEmpty()) {
            throw new BasketValidationConflictException(alreadySold);
        }

        // Reject the whole validation if any lot in the basket already has a member sold in a
        // committed sale (FR-109, story 5.8) — the sibling in this basket can never be sold. Kept
        // even though a lot is now reserved at scan time: that reservation is gone once the basket
        // that held it was validated/cancelled, this check is not.
        for (Item representative : ItemPricing.distinctByLot(items)) {
            Lot lot = representative.getLot();
            if (lot != null && itemRepository.existsByLotIdAndSoldTrue(lot.getId())) {
                // This basket can never validate this lot: the pre-check will keep failing as long
                // as the lot stays in it. Its reservation was committed by an earlier addItem
                // transaction, so releasing it here in a separate REQUIRES_NEW transaction — before
                // this @Transactional validate() rolls back with the exception — is the only way it
                // survives; without it the dead lot stays blocked at every terminal (Blind Hunter
                // #7, story 4.8 review).
                basketCancellationService.releaseLotReservationInNewTransaction(lot.getId(), basket.getId());
                throw new LotAlreadySoldException(lot.getId());
            }
        }

        BigDecimal total = ItemPricing.computeTotal(items);
        if (dto.paymentMethod() == PaymentMethod.CASH
                && dto.amountGiven() != null
                && dto.amountGiven().compareTo(total) < 0) {
            throw new InvalidAmountGivenException();
        }

        Sale sale = new Sale();
        sale.setEdition(edition);
        sale.setUser(userRepository.getReferenceById(userId));
        sale.setPaymentMethod(dto.paymentMethod());
        sale.setAmountGiven(dto.amountGiven());
        sale.setTotal(total);
        sale.setSoldAt(LocalDateTime.now());
        sale = saleRepository.save(sale);

        // Flushed one item at a time (rather than a single batched flush) so that every item that
        // actually lost the optimistic-lock race is identified precisely (AC 8) — a single batched
        // flush only ever surfaces the first StaleStateException via getIdentifier(), forcing a
        // guess (previously: blaming the whole basket) for the rest.
        List<ConflictingItemDto> conflicts = new ArrayList<>();
        for (Item item : items) {
            item.setSold(true);
            item.setSale(sale);
            try {
                itemRepository.saveAndFlush(item);
            } catch (ObjectOptimisticLockingFailureException e) {
                conflicts.add(new ConflictingItemDto(item.getId(), item.getName()));
            } catch (JpaSystemException e) {
                // MariaDB (unlike H2) can detect this same row-version race at the storage-engine
                // level and reject the UPDATE outright (native error 1020) instead of letting it
                // apply with zero affected rows — Hibernate's MariaDB dialect surfaces that as a
                // SnapshotIsolationException, which Spring only ever translates to the generic
                // JpaSystemException, never to ObjectOptimisticLockingFailureException (story 4.4).
                // Walks the full cause chain (not just e.getCause()) so this still matches if a
                // future Spring/Hibernate version adds another layer of wrapping around the cause.
                if (!isCausedBy(e, SnapshotIsolationException.class)) {
                    throw e;
                }
                conflicts.add(new ConflictingItemDto(item.getId(), item.getName()));
            }
        }
        if (!conflicts.isEmpty()) {
            throw new BasketValidationConflictException(conflicts);
        }

        // FR-109: release every lot this basket reserved (deterministic; the FK ON DELETE SET NULL
        // is only a safety net behind this).
        lotRepository.releaseAllByBasketId(basket.getId());
        basketRepository.delete(basket);

        BigDecimal changeDue = dto.paymentMethod() == PaymentMethod.CASH && dto.amountGiven() != null
                ? dto.amountGiven().subtract(total)
                : null;
        return new SaleDto(sale.getId(), total, sale.getPaymentMethod(), sale.getAmountGiven(), changeDue);
    }

    private Basket createBasket(Edition edition, Long userId) {
        Basket basket = new Basket();
        basket.setEdition(edition);
        basket.setUser(userRepository.getReferenceById(userId));
        touch(basket);
        try {
            return basketRepository.saveAndFlush(basket);
        } catch (DataIntegrityViolationException e) {
            // Two concurrent requests (e.g. two browser tabs) both racing to create the first
            // basket for this user/edition: the loser re-resolves the winner's row instead of
            // surfacing a 500 for a race that isn't actually an error from the user's perspective.
            return basketRepository.findByEditionIdAndUserId(edition.getId(), userId)
                    .orElseThrow(() -> e);
        }
    }

    /**
     * Stamps the basket's last sign of life (FR-110 / FR-066). Called on creation and on every
     * successful mutating action so {@link BasketReaperService} never sweeps a basket a cashier is
     * actively using. The basket is a managed entity here — the surrounding {@code @Transactional}
     * flushes the column on commit, no explicit save needed (same as the rest of this service).
     */
    private void touch(Basket basket) {
        basket.setLastSeenAt(LocalDateTime.now());
    }

    private Basket requireOwnedBasket(Long basketId, Long userId) {
        Basket basket = basketRepository.findById(basketId)
                .orElseThrow(() -> new BasketNotFoundException(basketId));
        if (!basket.getUser().getId().equals(userId)) {
            // Never distinguish "doesn't exist" from "belongs to someone else" (IDOR, AC 3).
            throw new BasketNotFoundException(basketId);
        }
        return basket;
    }

    private BasketDto toDto(Basket basket) {
        List<Item> items = basketItemsOf(basket);
        List<ScanResultDto> itemDtos = items.stream().map(scanResultMapper::toDto).toList();
        return new BasketDto(basket.getId(), itemDtos, buildLotGroups(items), ItemPricing.computeTotal(items));
    }

    /**
     * One group per distinct lot present in {@code items} (order of first appearance), each
     * reporting how many of its members are in this basket versus the lot's total membership —
     * the frontend uses this to render the incomplete/complete lot states (AC 2, 3).
     */
    private List<LotGroupDto> buildLotGroups(List<Item> items) {
        List<LotGroupDto> groups = new ArrayList<>();
        for (Item representative : ItemPricing.distinctByLot(items)) {
            Lot lot = representative.getLot();
            if (lot == null) {
                continue;
            }
            long scannedCount = items.stream()
                    .filter(item -> item.getLot() != null && lot.getId().equals(item.getLot().getId()))
                    .count();
            groups.add(new LotGroupDto(lot.getId(), lot.getName(), lot.getGlobalPrice(), (int) scannedCount, lot.getItems().size()));
        }
        return groups;
    }

    private List<Item> basketItemsOf(Basket basket) {
        return basketItemRepository.findAllByBasketIdOrderById(basket.getId()).stream().map(BasketItem::getItem).toList();
    }

    /**
     * Claims the lot for {@code basketId} (FR-109, SCP 2026-09-03). Every failed claim becomes a
     * clean {@link LotReservedException} (409), never a 500: {@code reserveLot} matching 0 rows on
     * H2 / on MariaDB without snapshot isolation; and, on MariaDB with {@code innodb_snapshot_isolation}
     * on (default since 11.6.2), the racing {@code UPDATE} being rejected outright — surfaced either
     * as a Hibernate {@link SnapshotIsolationException} or as a raw MariaDB error 1020
     * {@link SQLException} (story 4.8 review, D1). That outright rejection also fires when the row
     * now holds this same basket's id — two members of one lot scanned into the same basket at the
     * same instant, the second re-claiming a lot the first already reserved — so on a snapshot race
     * the current holder is re-read: our own {@code basketId} means the claim effectively succeeded.
     * Anything else inside the {@link JpaSystemException} is a real failure and is rethrown.
     */
    private void reserveLotForBasket(Long lotId, Long basketId) {
        try {
            if (lotRepository.reserveLot(lotId, basketId, LocalDateTime.now()) == 0) {
                throw new LotReservedException(lotId);
            }
        } catch (JpaSystemException e) {
            if (!isSnapshotIsolationRace(e)) {
                throw e;
            }
            if (!basketId.equals(currentReservationHolder(lotId))) {
                throw new LotReservedException(lotId);
            }
        }
    }

    private Long currentReservationHolder(Long lotId) {
        return lotRepository.findById(lotId).map(Lot::getReservedByBasketId).orElse(null);
    }

    /**
     * True when {@code throwable} is MariaDB rejecting a racing {@code UPDATE} under
     * {@code innodb_snapshot_isolation}: Hibernate's {@link SnapshotIsolationException}, or a raw
     * MariaDB error 1020 ("Record has changed since last read") {@link SQLException}, anywhere in
     * the cause chain.
     */
    private static boolean isSnapshotIsolationRace(Throwable throwable) {
        if (isCausedBy(throwable, SnapshotIsolationException.class)) {
            return true;
        }
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException && sqlException.getErrorCode() == 1020) {
                return true;
            }
        }
        return false;
    }

    /**
     * Walks the full cause chain of {@code throwable} for an instance of {@code type} — unlike
     * {@code getCause()}, tolerant of extra wrapping layers a future Spring/Hibernate version
     * might introduce between the two.
     */
    private static boolean isCausedBy(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable cause = throwable.getCause(); cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }
}
