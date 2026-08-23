package org.pluribourse.domain.archive.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Frozen edition-report snapshot (FR-059, story 2.7 follow-up fix), populated only at archive
 * time — {@code Item}/{@code Settlement} rows are deleted during archiving, so this is the only
 * source left for {@code ReportService.getEditionReport} once the edition is archived. A strict
 * 1:1 with {@code Edition}: the edition's own id is reused as this row's primary key, no separate
 * identity column.
 */
@Entity
@Table(name = "edition_archive_snapshots")
@Getter
@Setter
@NoArgsConstructor
public class EditionArchiveSnapshot {

    @Id
    @Column(name = "edition_id")
    private Long editionId;

    @Column(name = "sold_item_count", nullable = false)
    private Long soldItemCount;

    @Column(name = "unsold_item_count", nullable = false)
    private Long unsoldItemCount;

    @Column(name = "gross_revenue", nullable = false, precision = 10, scale = 2)
    private BigDecimal grossRevenue;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal commission;

    @Column(name = "cash_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal cashTotal;

    @Column(name = "check_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal checkTotal;

    @Column(name = "card_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal cardTotal;

    @Column(name = "net_payout_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal netPayoutTotal;

    @Column(name = "association_revenue_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal associationRevenueTotal;
}
