package org.pluribourse.domain.pos.entity;

import jakarta.persistence.*;
import lombok.*;
import org.pluribourse.domain.edition.entity.*;
import org.pluribourse.domain.user.entities.*;

import java.time.*;
import java.util.*;

@Entity
@Table(name = "baskets")
@Getter
@Setter
@NoArgsConstructor
public class Basket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "edition_id", nullable = false)
    private Edition edition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "basket", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BasketItem> items = new ArrayList<>();

    // FR-110 / FR-066 (SCP 2026-09-04): last sign of life from the cashier's POS page — set on
    // basket creation, on every successful POS action, and on every heartbeat. Read only by
    // BasketReaperService, which cancels baskets whose last_seen_at is older than
    // pos.basket.heartbeat.dead-threshold. Nullable in the schema so the addColumn was legal on
    // pre-migration rows (changelog 036); always written by the code, never left null.
    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;
}
