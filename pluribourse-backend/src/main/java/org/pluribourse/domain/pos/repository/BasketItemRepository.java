package org.pluribourse.domain.pos.repository;

import org.pluribourse.domain.pos.entity.BasketItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BasketItemRepository extends JpaRepository<BasketItem, Long> {

    Optional<BasketItem> findByBasketIdAndItemId(Long basketId, Long itemId);

    /**
     * Used instead of {@code Basket.items} (the mapped lazy collection) everywhere the basket's
     * current content must be read back within the same transaction as a just-applied add/remove —
     * a plain lazy-collection access does not reliably auto-flush a pending insert/delete first,
     * while this explicit query does (same JOIN FETCH rationale as {@code ItemRepository}'s
     * per-seller queries: avoids one lazy load of {@code item}/{@code item.lot} per basket row).
     */
    @Query("SELECT bi FROM BasketItem bi JOIN FETCH bi.item i LEFT JOIN FETCH i.lot WHERE bi.basket.id = :basketId ORDER BY bi.id ASC")
    List<BasketItem> findAllByBasketIdOrderById(@Param("basketId") Long basketId);

    @Query("SELECT bi FROM BasketItem bi JOIN bi.item i WHERE bi.basket.id = :basketId AND i.lot.id = :lotId")
    List<BasketItem> findAllByBasketIdAndItemLotId(@Param("basketId") Long basketId, @Param("lotId") Long lotId);
}
