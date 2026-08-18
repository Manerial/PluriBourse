package org.pluribourse.domain.pos.repository;

import org.pluribourse.domain.pos.entity.Sale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    /**
     * Daily sales report (story 5.3, FR-054): every Sale validated within the given calendar-day
     * window, used to compute gross revenue and the payment-method breakdown in memory (BigDecimal
     * sums, same convention as ItemPricing — no SQL-level aggregation anywhere else in this project).
     */
    @Query("SELECT s FROM Sale s WHERE s.edition.id = :editionId AND s.soldAt >= :dayStart AND s.soldAt < :dayEnd")
    List<Sale> findAllByEditionIdAndSoldAtBetween(@Param("editionId") Long editionId,
            @Param("dayStart") LocalDateTime dayStart, @Param("dayEnd") LocalDateTime dayEnd);
}
