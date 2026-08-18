package org.pluribourse.domain.report.service;

import lombok.RequiredArgsConstructor;
import org.pluribourse.domain.edition.entity.Edition;
import org.pluribourse.domain.item.entity.Item;
import org.pluribourse.domain.item.repository.ItemRepository;
import org.pluribourse.domain.item.service.ItemPricing;
import org.pluribourse.domain.item.service.PhaseGuard;
import org.pluribourse.domain.pos.entity.Sale;
import org.pluribourse.domain.pos.repository.SaleRepository;
import org.pluribourse.domain.report.dto.DailySalesReportDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Story 5.3 — computes the daily sales report (FR-054, FR-094): items sold/unsold, gross revenue,
 * commission and payment-method breakdown for the current calendar day. Gross revenue is derived
 * from {@link Sale#getTotal()} (already lot-aware, computed once at basket validation, story 4.2)
 * rather than re-derived via {@link ItemPricing#computeTotal}, avoiding the partially-sold-lot
 * pitfall already hit and fixed in {@code SettlementReportRenderer} (story 5.2 review).
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final SaleRepository saleRepository;
    private final ItemRepository itemRepository;

    @Transactional(readOnly = true)
    public DailySalesReportDto getDailyReport(Edition edition) {
        PhaseGuard.requireSalePhase(edition);

        LocalDate today = LocalDate.now();
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);

        List<Sale> todaysSales = saleRepository.findAllByEditionIdAndSoldAtBetween(edition.getId(), dayStart, dayEnd);
        List<Item> soldItemsToday = itemRepository.findAllSoldByEditionIdAndSoldAtBetween(edition.getId(), dayStart, dayEnd);
        List<Item> unsoldItems = itemRepository.findAllUnsoldByEditionId(edition.getId());

        BigDecimal cash = BigDecimal.ZERO;
        BigDecimal check = BigDecimal.ZERO;
        BigDecimal card = BigDecimal.ZERO;
        for (Sale sale : todaysSales) {
            switch (sale.getPaymentMethod()) {
                case CASH -> cash = cash.add(sale.getTotal());
                case CHECK -> check = check.add(sale.getTotal());
                case CARD -> card = card.add(sale.getTotal());
                default -> throw new IllegalStateException("Unhandled payment method: " + sale.getPaymentMethod());
            }
        }
        BigDecimal grossRevenue = cash.add(check).add(card).setScale(2, RoundingMode.HALF_UP);
        BigDecimal commission = ItemPricing.computeCommission(grossRevenue, edition.getCommissionRate()).setScale(2, RoundingMode.HALF_UP);
        long soldItemCount = ItemPricing.distinctByLot(soldItemsToday).size();
        long unsoldItemCount = ItemPricing.distinctByLot(unsoldItems).size();

        return new DailySalesReportDto(today, soldItemCount, unsoldItemCount, grossRevenue, commission,
                cash.setScale(2, RoundingMode.HALF_UP), check.setScale(2, RoundingMode.HALF_UP), card.setScale(2, RoundingMode.HALF_UP));
    }
}
