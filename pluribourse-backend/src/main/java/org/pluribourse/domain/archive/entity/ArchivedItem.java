package org.pluribourse.domain.archive.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pluribourse.domain.edition.entity.Edition;

import java.math.BigDecimal;

@Entity
@Table(name = "archived_items")
@Getter
@Setter
@NoArgsConstructor
public class ArchivedItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "edition_id", nullable = false)
    private Edition edition;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "category_name", nullable = false, length = 100)
    private String categoryName;

    @Column(nullable = false)
    private boolean sold;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    // Opaque discriminator for the originating lot (Story 6.3) — null for standalone items and for
    // rows archived before migration 034. No FK: archiving leaves orphaned lots rows behind.
    @Column(name = "lot_ref")
    private Long lotRef;

    @Column(name = "lot_name", length = 200)
    private String lotName;
}
