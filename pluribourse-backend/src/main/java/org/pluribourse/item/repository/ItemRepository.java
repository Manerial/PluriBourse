package org.pluribourse.item.repository;

import org.pluribourse.item.entity.Item;
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
