package org.pluribourse.edition.entity;

import jakarta.persistence.*;
import lombok.*;
import org.pluribourse.user.enums.*;

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
}
