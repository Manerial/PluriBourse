package org.pluribourse.domain.item.repository;

import org.pluribourse.domain.print.service.*;
import org.pluribourse.domain.item.entity.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long> {

    boolean existsBySellerProfileId(Long sellerProfileId);

    boolean existsByEditionId(Long editionId);

    List<Item> findAllBySellerProfileIdOrderByNameAsc(Long sellerProfileId);

    /**
     * Print order for the deposit roll (FR-030) — entry order, not the alphabetical UI listing
     * above. Eagerly fetches {@code edition}/{@code lot} because the returned items are captured
     * into a {@link PrintJob} closure and rendered later on the print
     * queue's consumer thread (story 3.4), long after this method's transaction/session has closed —
     * an uninitialized lazy proxy would throw LazyInitializationException at that point.
     */
    @Query("SELECT i FROM Item i JOIN FETCH i.edition JOIN FETCH i.sellerProfile LEFT JOIN FETCH i.lot WHERE i.sellerProfile.id = :sellerProfileId ORDER BY i.itemNumber ASC")
    List<Item> findAllBySellerProfileIdOrderByItemNumberAsc(@Param("sellerProfileId") Long sellerProfileId);

    /**
     * Lot siblings in creation order, used to compute a lot item's "X/N" position (FR-045).
     */
    List<Item> findAllByLotIdOrderById(Long lotId);

    /**
     * excludeItemId lets a category reassignment (AC 5) query the table state as if the item
     * being reassigned did not exist yet — otherwise its own (stale) row, still visible via
     * Hibernate's pre-query auto-flush, would bias the result it is meant to help compute.
     * DISTINCT is required: a seller with 2+ items already in the category returns one row per
     * item (all sharing the same tableNumber by construction) — without it, getSingleResult()
     * throws NonUniqueResultException as soon as a second item exists.
     */
    @Query("""
            SELECT DISTINCT i.tableNumber FROM Item i
            WHERE i.sellerProfile.id = :sellerProfileId AND i.category.id = :categoryId
              AND (:excludeItemId IS NULL OR i.id <> :excludeItemId)
            """)
    Optional<Integer> findTableNumberBySellerProfileIdAndCategoryId(
            @Param("sellerProfileId") Long sellerProfileId, @Param("categoryId") Long categoryId,
            @Param("excludeItemId") Long excludeItemId);

    @Query("""
            SELECT i.tableNumber, COUNT(i) FROM Item i
            WHERE i.edition.id = :editionId AND i.tableNumber IN :tableNumbers
              AND (:excludeItemId IS NULL OR i.id <> :excludeItemId)
            GROUP BY i.tableNumber
            """)
    List<Object[]> countByTableNumber(@Param("editionId") Long editionId, @Param("tableNumbers") Collection<Integer> tableNumbers,
                                      @Param("excludeItemId") Long excludeItemId);
}
