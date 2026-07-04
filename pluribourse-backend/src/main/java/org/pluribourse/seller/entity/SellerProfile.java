package org.pluribourse.seller.entity;

import jakarta.persistence.*;
import lombok.*;
import org.pluribourse.edition.entity.*;

@Entity
@Table(name = "seller_profiles")
@Getter
@Setter
@NoArgsConstructor
public class SellerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "edition_id", nullable = false)
    private Edition edition;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, length = 30)
    private String phone;

    public boolean canBeDeleted(boolean hasNoRegisteredArticles) {
        PhaseType phase = edition.getPhase();
        boolean isOnDeletablePhase = phase == PhaseType.DEPOSIT;
        return isOnDeletablePhase && hasNoRegisteredArticles;
    }
}
