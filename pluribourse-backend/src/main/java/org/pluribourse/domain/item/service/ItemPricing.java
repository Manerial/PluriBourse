package org.pluribourse.domain.item.service;

import org.pluribourse.domain.item.entity.Item;
import org.pluribourse.domain.item.entity.Lot;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Shared between PosBasketService and DepositSlipRenderer so the total-computation rule is
 * defined once. A lot item never carries its own {@code price} — its {@code Lot} carries a
 * single global price that must be counted exactly once per distinct lot present in the given
 * items, however many of its items are present.
 */
public final class ItemPricing {

    private ItemPricing() {
    }

    public static BigDecimal computeTotal(List<Item> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (Item item : distinctByLot(items)) {
            total = total.add(item.getLot() != null ? item.getLot().getGlobalPrice() : item.getPrice());
        }
        return total;
    }

    /**
     * Filters {@code items} down to one representative per distinct lot (the first member
     * encountered, order preserved) plus every standalone item — the shared basis for any
     * lot-aware total, count or listing of the given items.
     */
    public static List<Item> distinctByLot(List<Item> items) {
        List<Item> result = new ArrayList<>();
        Set<Long> seenLotIds = new HashSet<>();
        for (Item item : items) {
            Lot lot = item.getLot();
            if (lot == null || seenLotIds.add(lot.getId())) {
                result.add(item);
            }
        }
        return result;
    }
}
