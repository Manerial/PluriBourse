package org.pluribourse.domain.item.entity;

import jakarta.persistence.*;
import lombok.*;
import org.pluribourse.domain.edition.entity.*;
import org.pluribourse.domain.seller.entity.*;

import java.math.*;
import java.util.*;

@Entity
@Table(name = "lots")
@Getter
@Setter
@NoArgsConstructor
public class Lot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "edition_id", nullable = false)
    private Edition edition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_profile_id", nullable = false)
    private SellerProfile sellerProfile;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "global_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal globalPrice;

    // Read side of Item.lot (mappedBy — no new FK, items.lot_id already owns the relationship).
    // Ordered by id, matching ItemRepository#findAllByLotIdOrderById, the query this replaces at
    // call sites that switch to it.
    @OneToMany(mappedBy = "lot", fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    private List<Item> items = new ArrayList<>();

    // Optimistic locking: guards against lost updates on concurrent lot edits (LotService.update).
    // No dedicated conflict handling — relies on Hibernate's default behavior.
    @Version
    private Long version;
}
