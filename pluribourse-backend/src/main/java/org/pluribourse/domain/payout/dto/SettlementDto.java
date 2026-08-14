package org.pluribourse.domain.payout.dto;

import org.pluribourse.domain.payout.entity.SettlementStatus;

import java.math.BigDecimal;

public record SettlementDto(
        Long sellerId,
        String firstName,
        String lastName,
        String phone,
        String email,
        BigDecimal amountDue,
        SettlementStatus status) {
}
