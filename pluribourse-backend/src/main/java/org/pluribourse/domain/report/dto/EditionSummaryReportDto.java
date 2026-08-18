package org.pluribourse.domain.report.dto;

import java.math.BigDecimal;

public record EditionSummaryReportDto(
        long soldItemCount,
        long unsoldItemCount,
        BigDecimal grossRevenue,
        BigDecimal commission,
        BigDecimal cashTotal,
        BigDecimal checkTotal,
        BigDecimal cardTotal) {
}
