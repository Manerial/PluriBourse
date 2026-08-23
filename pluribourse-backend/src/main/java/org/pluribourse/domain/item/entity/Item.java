package org.pluribourse.domain.item.entity;

import jakarta.persistence.*;
import lombok.*;
import org.pluribourse.domain.edition.entity.*;
import org.pluribourse.domain.pos.entity.*;
import org.pluribourse.domain.seller.entity.*;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id")
    private Lot lot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id")
    private Sale sale;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = true, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private boolean incomplete;

    @Column(nullable = false)
    private boolean sold;

    @Column(length = 500)
    private String comment;

    @Column(name = "table_number", nullable = false)
    private Integer tableNumber;

    @Column(name = "item_number", nullable = false)
    private Integer itemNumber;

    // Optimistic locking (story 4.4): detects concurrent sale of the same item from two POS terminals.
    @Version
    private Long version;

    /**
     * Max value a seller/item number can hold in the fixed 4-digit barcode format (FR-026).
     */
    public static final int MAX_BARCODE_SEGMENT = 9999;

    /**
     * Code 128 payload (FR-026): 4-digit seller number within the edition + 4-digit item number
     * within the seller's inventory. Never persisted — always derivable from the two numbers.
     */
    public String getBarcode() {
        return String.format("%04d%04d", requireFourDigits(sellerProfile.getSellerNumber()), requireFourDigits(itemNumber));
    }

    public String getFormattedBarcode() {
        return String.format("%04d-%04d", requireFourDigits(sellerProfile.getSellerNumber()), requireFourDigits(itemNumber));
    }

    /**
     * Guards the fixed 8-digit barcode contract (FR-026, AC1): %04d does not truncate past 4 digits.
     */
    private static int requireFourDigits(int number) {
        if (number < 0 || number > MAX_BARCODE_SEGMENT) {
            throw new IllegalStateException("Barcode segment " + number + " no longer fits the fixed 4-digit format (FR-026)");
        }
        return number;
    }
}
