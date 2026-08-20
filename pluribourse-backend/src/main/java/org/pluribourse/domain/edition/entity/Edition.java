package org.pluribourse.domain.edition.entity;

import jakarta.persistence.*;
import lombok.*;
import org.pluribourse.domain.user.enums.*;

import java.math.*;
import java.time.*;

@Entity
@Table(name = "editions")
@Getter
@Setter
@NoArgsConstructor
public class Edition {

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PhaseType phase;

    @Column(name = "commission_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_language", nullable = false, length = 2)
    private Language documentLanguage;

    @Column(name = "created_at", nullable = false)
    private LocalDate createdAt;

    @Column(name = "is_archived", nullable = false)
    private boolean archived = false;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /**
     * Next seller number to assign within this edition (FR-026) — a persisted counter, not
     * MAX(sellerNumber)+1: a deleted seller (FR-021) must never free its number for reuse.
     */
    @Column(name = "next_seller_number", nullable = false)
    private Integer nextSellerNumber = 1;

    /**
     * Frozen edition-report snapshot (FR-059), populated only at archive time —
     * {@code Item}/{@code Settlement} rows are deleted during archiving, so these columns are the
     * only source left for {@code ReportService.getEditionReport} once {@link #archived} is true.
     */
    @Column(name = "archived_sold_item_count")
    private Long archivedSoldItemCount;

    @Column(name = "archived_unsold_item_count")
    private Long archivedUnsoldItemCount;

    @Column(name = "archived_gross_revenue", precision = 10, scale = 2)
    private BigDecimal archivedGrossRevenue;

    @Column(name = "archived_commission", precision = 10, scale = 2)
    private BigDecimal archivedCommission;

    @Column(name = "archived_cash_total", precision = 10, scale = 2)
    private BigDecimal archivedCashTotal;

    @Column(name = "archived_check_total", precision = 10, scale = 2)
    private BigDecimal archivedCheckTotal;

    @Column(name = "archived_card_total", precision = 10, scale = 2)
    private BigDecimal archivedCardTotal;

    @Column(name = "archived_net_payout_total", precision = 10, scale = 2)
    private BigDecimal archivedNetPayoutTotal;

    @Column(name = "archived_association_revenue_total", precision = 10, scale = 2)
    private BigDecimal archivedAssociationRevenueTotal;
}
