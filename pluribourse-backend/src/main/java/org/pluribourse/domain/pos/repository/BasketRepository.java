package org.pluribourse.domain.pos.repository;

import org.pluribourse.domain.pos.entity.Basket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BasketRepository extends JpaRepository<Basket, Long> {

    Optional<Basket> findByEditionIdAndUserId(Long editionId, Long userId);

    List<Basket> findAllByEditionId(Long editionId);

    /**
     * Story 4.9 — baskets the inactive-terminal reaper should cancel: last sign of life older than
     * {@code threshold} ({@code now - pos.basket.heartbeat.dead-threshold}). The
     * {@code OR b.lastSeenAt IS NULL} clause is a belt-and-suspenders for a basket that predates
     * changelog 036 (none in prod): {@code createBasket} always writes the column, so a live basket
     * never has it null, and a null one is stale by definition anyway.
     */
    @Query("SELECT b FROM Basket b WHERE b.lastSeenAt IS NULL OR b.lastSeenAt < :threshold")
    List<Basket> findStale(@Param("threshold") LocalDateTime threshold);
}
