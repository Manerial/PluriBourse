package org.pluribourse.domain.pos.entity;

import jakarta.persistence.*;
import lombok.*;
import org.pluribourse.domain.item.entity.*;

@Entity
@Table(name = "basket_items")
@Getter
@Setter
@NoArgsConstructor
public class BasketItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "basket_id", nullable = false)
    private Basket basket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;
}
