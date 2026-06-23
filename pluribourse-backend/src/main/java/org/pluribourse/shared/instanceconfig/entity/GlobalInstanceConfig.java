package org.pluribourse.shared.instanceconfig.entity;

import jakarta.persistence.*;
import lombok.*;
import org.pluribourse.user.enums.*;

import java.math.*;

@Entity
@Table(name = "global_instance_config")
@Getter
@Setter
@NoArgsConstructor
public class GlobalInstanceConfig {

    @Id  // No @GeneratedValue — singleton, always id=1, set by migration 004
    private Long id;

    @Column(name = "association_name", nullable = false)
    private String associationName;

    @Column(name = "default_commission_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal defaultCommissionRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_document_language", nullable = false, length = 2)
    private Language defaultDocumentLanguage;
}
