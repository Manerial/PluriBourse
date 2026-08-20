package org.pluribourse.domain.item.repository;

import org.pluribourse.domain.item.entity.Item;
import org.pluribourse.domain.print.service.PrintJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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

    /**
     * Catalog listing (Story 6.1): eagerly fetches {@code sellerProfile}/{@code category} because
     * {@code ItemCatalogService} filters/sorts the entire edition's item list in memory before
     * paginating — without JOIN FETCH, touching those associations while filtering would trigger
     * up to ~1700 extra lazy-load queries per catalog page view (same pattern as
     * {@link #findAllBySellerProfileIdOrderByItemNumberAsc}). The explicit ORDER BY gives a stable
     * base order: JPageFlow skips sorting entirely when no {@code sort} param is supplied, and
     * without one, pagination across requests could return duplicate or skipped rows.
     */
    @Query("SELECT i FROM Item i JOIN FETCH i.sellerProfile JOIN FETCH i.category LEFT JOIN FETCH i.lot WHERE i.edition.id = :editionId ORDER BY i.id ASC")
    List<Item> findAllByEditionIdForCatalog(@Param("editionId") Long editionId);

    /**
     * Resolves a POS scan (story 4.1): the barcode is never persisted, only derivable from
     * {@code sellerProfile.sellerNumber} (scoped per edition) + {@code itemNumber}. No
     * {@code JOIN FETCH} on {@code sellerProfile} — {@link org.pluribourse.domain.pos.dto.ScanResultDto}
     * never exposes seller data, so eagerly fetching it here would load an association never read.
     */
    @Query("SELECT i FROM Item i WHERE i.edition.id = :editionId " +
            "AND i.sellerProfile.sellerNumber = :sellerNumber AND i.itemNumber = :itemNumber")
    Optional<Item> findByEditionIdAndSellerNumberAndItemNumber(
            @Param("editionId") Long editionId, @Param("sellerNumber") int sellerNumber, @Param("itemNumber") int itemNumber);

    /**
     * Buyer invoice (story 4.5): {@code JOIN FETCH i.lot} for the same reason as
     * {@link #findAllBySellerProfileIdOrderByItemNumberAsc} — the items are captured into a
     * {@link PrintJob} executed later on the print queue's consumer thread, after this method's
     * transaction/session has closed. Neither {@code edition} nor {@code sellerProfile} is
     * fetched: {@code InvoiceRenderer} reads neither (the edition name is resolved separately, see
     * {@code PosInvoicePrintService}).
     */
    @Query("SELECT i FROM Item i LEFT JOIN FETCH i.lot WHERE i.sale.id = :saleId ORDER BY i.id ASC")
    List<Item> findAllBySaleIdOrderById(@Param("saleId") Long saleId);

    /**
     * Settlement list (story 5.1): sold items across the whole edition, grouped by seller in
     * memory afterwards. JOIN FETCH on lot only — sellerProfile is read only via its already-
     * cached id (item.getSellerProfile().getId()) to key the grouping, which never triggers a
     * lazy load on a Hibernate proxy (same reasoning as ScanResultDto's scan query).
     */
    @Query("SELECT i FROM Item i LEFT JOIN FETCH i.lot WHERE i.edition.id = :editionId AND i.sold = true")
    List<Item> findAllByEditionIdAndSoldTrue(@Param("editionId") Long editionId);

    /**
     * Single-seller settlement amount (story 5.1): scoped by sellerProfileId rather than
     * re-scanning the whole edition's sold items in memory like {@link #findAllByEditionIdAndSoldTrue}
     * does for the bulk list — {@code settle}/{@code markUnclaimed} only ever need one seller's total.
     */
    @Query("SELECT i FROM Item i LEFT JOIN FETCH i.lot WHERE i.sellerProfile.id = :sellerProfileId AND i.sold = true")
    List<Item> findAllBySellerProfileIdAndSoldTrue(@Param("sellerProfileId") Long sellerProfileId);

    /**
     * Seller sales report PDF (story 5.2): all items (sold and unsold) for one seller, captured
     * into a PrintJob closure like {@link #findAllBySaleIdOrderById} — JOIN FETCH category in
     * addition to lot (unlike {@link #findAllBySellerProfileIdOrderByItemNumberAsc}), since unsold
     * items must show their category name (FR-050) and the renderer never touches sellerProfile/edition.
     */
    @Query("SELECT i FROM Item i JOIN FETCH i.category LEFT JOIN FETCH i.lot WHERE i.sellerProfile.id = :sellerProfileId ORDER BY i.itemNumber ASC")
    List<Item> findAllBySellerProfileIdForSettlementReport(@Param("sellerProfileId") Long sellerProfileId);

    /**
     * Daily sales report (story 5.3, FR-054): unsold items in the active edition as of now — a
     * snapshot, not scoped to any calendar day (an unsold item has no sale date to filter by; only
     * the sold query below is day-scoped). JOIN FETCH lot for lot-aware counting via
     * {@link org.pluribourse.domain.item.service.ItemPricing#distinctByLot} — a lot with any member
     * still unsold counts once, same convention as every other lot-aware count in this codebase
     * (decision confirmed with the user at create-story: 1 per lot, not per member, for both the
     * sold and unsold counters).
     */
    @Query("SELECT i FROM Item i LEFT JOIN FETCH i.lot WHERE i.edition.id = :editionId AND i.sold = false")
    List<Item> findAllUnsoldByEditionId(@Param("editionId") Long editionId);

    /**
     * Daily sales report (story 5.3, FR-054): items sold within the given calendar-day window.
     * JOIN FETCH lot for lot-aware counting via {@link org.pluribourse.domain.item.service.ItemPricing#distinctByLot}
     * — a lot with any member sold inside the window counts once, matching every other lot-aware
     * count/total in this codebase.
     */
    @Query("SELECT i FROM Item i LEFT JOIN FETCH i.lot WHERE i.edition.id = :editionId AND i.sale.soldAt >= :dayStart AND i.sale.soldAt < :dayEnd")
    List<Item> findAllSoldByEditionIdAndSoldAtBetween(@Param("editionId") Long editionId,
            @Param("dayStart") LocalDateTime dayStart, @Param("dayEnd") LocalDateTime dayEnd);

    /**
     * Bulk settlement report printing (story 5.6, FR-097): every item (sold and unsold) across
     * the whole edition, grouped by seller in memory afterwards — same batched pattern as
     * {@link #findAllByEditionIdAndSoldTrue}, avoiding a per-seller N+1 query in the print loop
     * (NFR-001, ~100 sellers). JOIN FETCH category + lot, same as
     * {@link #findAllBySellerProfileIdForSettlementReport} (the per-seller equivalent used by the
     * single-report endpoint) — sellerProfile is read only via its already-cached id to key the
     * grouping, never triggering a lazy load. The double-key ORDER BY matters: once items are
     * regrouped by seller in memory ({@code Collectors.groupingBy}), the order inside each group
     * would otherwise not be guaranteed {@code itemNumber ASC}, as expected by
     * {@code SettlementReportRenderer} (same order as the per-seller query above).
     */
    @Query("SELECT i FROM Item i JOIN FETCH i.category LEFT JOIN FETCH i.lot WHERE i.edition.id = :editionId ORDER BY i.sellerProfile.id ASC, i.itemNumber ASC")
    List<Item> findAllByEditionIdForSettlementReport(@Param("editionId") Long editionId);
}
