package org.pluribourse.domain.pos.service;

import lombok.RequiredArgsConstructor;
import org.pluribourse.domain.edition.entity.Edition;
import org.pluribourse.domain.edition.service.EditionService;
import org.pluribourse.domain.item.entity.Item;
import org.pluribourse.domain.item.entity.Lot;
import org.pluribourse.domain.item.repository.ItemRepository;
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

    @Transactional
    public BasketDto getOrCreateCurrentBasket(Long userId) {
        Edition edition = editionService.getActiveEdition();
        PhaseGuard.requireSalePhase(edition);
        Basket basket = basketRepository.findByEditionIdAndUserId(edition.getId(), userId)
                .orElseGet(() -> createBasket(edition, userId));
        return toDto(basket);
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
        return toDto(basket);
    }

    @Transactional
    public BasketDto removeItem(Long basketId, Long itemId, Long userId) {
        PhaseGuard.requireSalePhase(editionService.getActiveEdition());
        Basket basket = requireOwnedBasket(basketId, userId);
        BasketItem basketItem = basketItemRepository.findByBasketIdAndItemId(basketId, itemId)
                .orElseThrow(() -> new BasketItemNotFoundException(basketId, itemId));
        basketItemRepository.delete(basketItem);
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
        return toDto(basket);
    }

    /**
     * Validates the basket's payment atomically: either every item is marked sold and the basket
     * is replaced by a {@code Sale}, or nothing is persisted at all (AC 4). Guards, in order: Sale
     * phase (AC 9), ownership (IDOR), non-empty basket, no item already sold, a sufficient CASH
     * amount, then a per-item optimistic-lock check (AC 8) so that every item that actually lost
     * the concurrent-sale race is reported precisely — not just the first one Hibernate happens to
     * surface — rather than falling back to blaming the whole basket.
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
