package org.pluribourse.domain.print.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "ignored_printers")
@Getter
@Setter
@NoArgsConstructor
public class IgnoredPrinter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "printer_bridge_id", nullable = false, unique = true, length = 32)
    private String printerBridgeId;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "ignored_at", nullable = false)
    private LocalDate ignoredAt;
}
