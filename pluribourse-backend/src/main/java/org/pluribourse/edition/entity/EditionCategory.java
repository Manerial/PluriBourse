package org.pluribourse.edition.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.*;

@Entity
@Table(name = "edition_categories",
        uniqueConstraints = @UniqueConstraint(columnNames = {"edition_id", "name"}))
@Getter
@Setter
@NoArgsConstructor
public class EditionCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "edition_id", nullable = false)
    private Edition edition;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @ElementCollection
    @CollectionTable(name = "category_table_assignments", joinColumns = @JoinColumn(name = "category_id"))
    @Column(name = "table_number")
    private Set<Integer> tableNumbers = new HashSet<>();
}
