package org.pluribourse.domain.item.service;

import lombok.*;
import org.pluribourse.domain.edition.entity.*;
import org.pluribourse.domain.edition.repository.*;
import org.pluribourse.domain.item.repository.*;
import org.pluribourse.domain.seller.entity.*;
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

    /**
     * NO_EXCLUSION is a sentinel, never a real id (IDENTITY starts at 1) — keeps the "exclude none"
     * case a valid non-empty JPQL IN-list instead of special-casing an empty Collection parameter.
     */
    private static final Set<Long> NO_EXCLUSION = Set.of(-1L);

    @Transactional
    public int assignTable(SellerProfile sellerProfile, EditionCategory category, Edition edition) {
        return assignTable(sellerProfile, category, edition, NO_EXCLUSION);
    }

    @Transactional
    public int assignTable(SellerProfile sellerProfile, EditionCategory category, Edition edition, Long excludeItemId) {
        return assignTable(sellerProfile, category, edition, excludeItemId == null ? NO_EXCLUSION : Set.of(excludeItemId));
    }

    /**
     * excludeItemIds must contain the ids of every item being reassigned together (AC 3: a lot
     * reassigns all its members' table in one operation, not one at a time) so none of them bias
     * the "already has a table" lookup or the load count via their own (still current, about to
     * change) rows. Pass {@link #NO_EXCLUSION} when assigning brand-new items.
     * <p>
     * Locks the category row first (joins the caller's transaction, held until it commits) so two
     * concurrent first-deposits into the same category serialize instead of both computing the
     * load count from a pre-insert state and picking the same table.
     */
    @Transactional
    public int assignTable(SellerProfile sellerProfile, EditionCategory category, Edition edition, Collection<Long> excludeItemIds) {
        Collection<Long> effectiveExcludeItemIds = excludeItemIds.isEmpty() ? NO_EXCLUSION : excludeItemIds;
        categoryRepository.lockById(category.getId());
        return itemRepository.findTableNumberBySellerProfileIdAndCategoryId(sellerProfile.getId(), category.getId(), effectiveExcludeItemIds)
                .orElseGet(() -> leastLoadedTable(category, edition, effectiveExcludeItemIds));
    }

    private int leastLoadedTable(EditionCategory category, Edition edition, Collection<Long> excludeItemIds) {
        Set<Integer> tableNumbers = category.getTableNumbers();
        Map<Integer, Long> countsByTable = new TreeMap<>();
        for (Integer tableNumber : tableNumbers) {
            countsByTable.put(tableNumber, 0L);
        }
        for (Object[] row : itemRepository.countByTableNumber(edition.getId(), tableNumbers, excludeItemIds)) {
            countsByTable.put((Integer) row[0], (Long) row[1]);
        }
        return countsByTable.entrySet().stream()
                .min(Map.Entry.<Integer, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .orElseThrow();
    }
}
