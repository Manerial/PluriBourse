package org.pluribourse.domain.print.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "registered_printers")
@Getter
@Setter
@NoArgsConstructor
public class Printer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "printer_bridge_id", nullable = false, length = 32)
    private String printerBridgeId;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PrinterType type;

    @Column(name = "width_mm")
    private Integer widthMm;

    @Version
    private Long version;
}
