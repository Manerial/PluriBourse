package org.pluribourse.domain.pos.entity;

import jakarta.persistence.*;
import lombok.*;
import org.pluribourse.domain.edition.entity.*;
import org.pluribourse.domain.user.entities.*;

import java.math.*;
import java.time.*;

@Entity
@Table(name = "sales")
@Getter
@Setter
@NoArgsConstructor
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "edition_id", nullable = false)
    private Edition edition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "amount_given", nullable = true, precision = 10, scale = 2)
    private BigDecimal amountGiven;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @Column(name = "sold_at", nullable = false)
    private LocalDateTime soldAt;
}
