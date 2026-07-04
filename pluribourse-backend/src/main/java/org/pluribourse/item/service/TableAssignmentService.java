package org.pluribourse.item.service;

import lombok.*;
import org.pluribourse.edition.entity.*;
import org.pluribourse.edition.repository.*;
import org.pluribourse.item.repository.*;
import org.pluribourse.seller.entity.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.*;

import java.util.*;

/**
 * Implements FR-023: a seller keeps a single table per category (all their items in
 * that category share it); the first item in a category for a seller lands on the
 * least-loaded table among those configured for the category, counting items across
 * all categories in the edition (a table is a shared physical resource, not per-category).
 */
@Service
@RequiredArgsConstructor
public class TableAssignmentService {

    private final ItemRepository itemRepository;
    private final EditionCategoryRepository categoryRepository;

    @Transactional
    public int assignTable(SellerProfile sellerProfile, EditionCategory category, Edition edition) {
        return assignTable(sellerProfile, category, edition, null);
    }

    /**
     * excludeItemId must be the id of the item being reassigned (AC 5) so it is left out of
     * both the "already has a table" lookup and the load count — otherwise it would count
     * towards its own old table/category. Pass null when assigning a brand-new item.
     * <p>
     * Locks the category row first (joins the caller's transaction, held until it commits) so
     * two concurrent first-deposits into the same category serialize instead of both computing
     * the load count from a pre-insert state and picking the same table.
     */
    @Transactional
    public int assignTable(SellerProfile sellerProfile, EditionCategory category, Edition edition, Long excludeItemId) {
        categoryRepository.lockById(category.getId());
        return itemRepository.findTableNumberBySellerProfileIdAndCategoryId(sellerProfile.getId(), category.getId(), excludeItemId)
                .orElseGet(() -> leastLoadedTable(category, edition, excludeItemId));
    }

    private int leastLoadedTable(EditionCategory category, Edition edition, Long excludeItemId) {
        Set<Integer> tableNumbers = category.getTableNumbers();
        Map<Integer, Long> countsByTable = new TreeMap<>();
        for (Integer tableNumber : tableNumbers) {
            countsByTable.put(tableNumber, 0L);
        }
        for (Object[] row : itemRepository.countByTableNumber(edition.getId(), tableNumbers, excludeItemId)) {
            countsByTable.put((Integer) row[0], (Long) row[1]);
        }
        return countsByTable.entrySet().stream()
                .min(Map.Entry.<Integer, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .orElseThrow();
    }
}
