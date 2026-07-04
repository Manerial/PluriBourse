package org.pluribourse.item.entity;

import jakarta.persistence.*;
import lombok.*;
import org.pluribourse.edition.entity.*;
import org.pluribourse.seller.entity.*;

import java.math.*;

@Entity
@Table(name = "items")
@Getter
@Setter
@NoArgsConstructor
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "edition_id", nullable = false)
    private Edition edition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_profile_id", nullable = false)
    private SellerProfile sellerProfile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private EditionCategory category;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private boolean incomplete;

    @Column(length = 500)
    private String comment;

    @Column(name = "table_number", nullable = false)
    private Integer tableNumber;

    @Version
    private Long version;
}
