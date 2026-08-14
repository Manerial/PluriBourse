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
import org.pluribourse.domain.payout.entity.Settlement;
import org.pluribourse.domain.payout.entity.SettlementStatus;
import org.pluribourse.domain.payout.exception.InvalidSettlementAmountException;
import org.pluribourse.domain.payout.exception.SellerAlreadySettledException;
import org.pluribourse.domain.payout.repository.SettlementRepository;
import org.pluribourse.domain.seller.entity.SellerProfile;
import org.pluribourse.domain.seller.exception.SellerNotFoundException;
import org.pluribourse.domain.seller.repository.SellerRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
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

    @Transactional(readOnly = true)
    public List<SettlementDto> getSettlements() {
        Edition edition = editionService.getActiveEdition();
        PhaseGuard.requirePostSalePhase(edition);

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
            return new SettlementDto(seller.getId(), seller.getFirstName(), seller.getLastName(),
                    seller.getPhone(), seller.getEmail(), amountDue, status);
        }).toList();
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
        return persistSettlement(seller, SettlementStatus.SETTLED, amount, amountDue);
    }

    @Transactional
    public SettlementDto markUnclaimed(Long sellerId) {
        Edition edition = editionService.getActiveEdition();
        PhaseGuard.requirePostSalePhase(edition);
        SellerProfile seller = requireSellerOfEdition(sellerId, edition);
        requireNotAlreadySettled(seller);

        BigDecimal amountDue = computeAmountDue(seller, edition);
        return persistSettlement(seller, SettlementStatus.UNCLAIMED, amountDue, amountDue);
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
     * {@code uk_settlements_seller_profile} unique constraint is the actual guard against two
     * concurrent calls for the same seller both passing that check; a violation here means a
     * genuine race was lost, translated to the same {@link SellerAlreadySettledException} the
     * read-side check throws (same rationale as PosBasketService's optimistic-lock handling).
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
        return new SettlementDto(seller.getId(), seller.getFirstName(), seller.getLastName(),
                seller.getPhone(), seller.getEmail(), amountDue, status);
    }
}
