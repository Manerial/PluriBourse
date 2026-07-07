package org.pluribourse.item.entity;

import jakarta.persistence.*;
import lombok.*;
import org.pluribourse.edition.entity.*;
import org.pluribourse.seller.entity.*;

import java.math.*;

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

    @Version
    private Long version;
}
