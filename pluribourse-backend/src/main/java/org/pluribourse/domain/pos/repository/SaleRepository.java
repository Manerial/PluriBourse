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

    /**
     * Edition summary report (story 5.4, FR-055): every Sale of the edition's whole lifetime,
     * not bounded to a single day (contrast with findAllByEditionIdAndSoldAtBetween, story 5.3).
     */
    @Query("SELECT s FROM Sale s WHERE s.edition.id = :editionId")
    List<Sale> findAllByEditionId(@Param("editionId") Long editionId);

    /**
     * Sales list screen (story 4.7, FR-108): every Sale of the edition, with its cashier eagerly
     * fetched — the list DTO exposes the cashier's username, and mapping happens after the
     * transaction closes (same JOIN FETCH rationale as ItemRepository.findAllByEditionIdForCatalog).
     * The explicit ORDER BY s.soldAt DESC is the default sort (AC 10); the s.id DESC tiebreaker
     * gives a deterministic base order for pagination when no {@code sort} param is supplied, even
     * when two concurrent POS terminals validate sales at the same soldAt instant. Filtering (date
     * range, cashier) and sort/page are applied in memory afterwards by SaleListService, not here.
     */
    @Query("SELECT s FROM Sale s JOIN FETCH s.user WHERE s.edition.id = :editionId ORDER BY s.soldAt DESC, s.id DESC")
    List<Sale> findAllByEditionIdForList(@Param("editionId") Long editionId);

    /**
     * Cashier selector of the sales list screen (story 4.7, AC 12): usernames of the cashiers with
     * at least one sale on the edition, distinct and alphabetically ordered.
     */
    @Query("SELECT DISTINCT s.user.username FROM Sale s WHERE s.edition.id = :editionId ORDER BY s.user.username")
    List<String> findDistinctCashierUsernamesByEditionId(@Param("editionId") Long editionId);
}
