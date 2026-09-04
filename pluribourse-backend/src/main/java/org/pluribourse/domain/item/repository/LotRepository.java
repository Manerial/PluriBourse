package org.pluribourse.domain.item.repository;

import org.pluribourse.domain.item.entity.Lot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface LotRepository extends JpaRepository<Lot, Long> {

    /**
     * FR-109 (SCP 2026-09-03) — optimistic claim token for the POS lot-reservation guard. Sets
     * {@code reserved_by_basket_id} to {@code basketId} only if the lot is currently free or already
     * held by that same basket; a bulk JPQL update so it runs now and short-circuits the persistence
     * context (the caller never re-reads the managed {@code Lot} afterwards). The
     * {@code OR l.reservedByBasketId = :basketId} clause makes a re-claim by the holding basket
     * idempotent (matched-rows semantics on MariaDB/H2 return 1 in that case).
     *
     * @return 1 if the reservation was taken (or already held by this basket), 0 if another basket holds it
     */
    @Modifying
    @Query("UPDATE Lot l SET l.reservedByBasketId = :basketId, l.reservedAt = :now "
            + "WHERE l.id = :lotId AND (l.reservedByBasketId IS NULL OR l.reservedByBasketId = :basketId)")
    int reserveLot(@Param("lotId") Long lotId, @Param("basketId") Long basketId, @Param("now") LocalDateTime now);

    /**
     * FR-109 — releases the lot's reservation, but only if {@code basketId} is still the holder
     * (same basket-scoped shape as {@link #releaseAllByBasketId}). A no-op when the lot is already
     * free or held by another basket, so a caller can never clear a reservation it does not own.
     *
     * @return 1 if this basket's reservation was cleared, 0 otherwise
     */
    @Modifying
    @Query("UPDATE Lot l SET l.reservedByBasketId = NULL, l.reservedAt = NULL "
            + "WHERE l.id = :lotId AND l.reservedByBasketId = :basketId")
    int releaseLot(@Param("lotId") Long lotId, @Param("basketId") Long basketId);

    @Modifying
    @Query("UPDATE Lot l SET l.reservedByBasketId = NULL, l.reservedAt = NULL WHERE l.reservedByBasketId = :basketId")
    int releaseAllByBasketId(@Param("basketId") Long basketId);
}
