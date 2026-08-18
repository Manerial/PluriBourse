package org.pluribourse.domain.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailySalesReportDto(
        LocalDate reportDate,
        long soldItemCount,
        long unsoldItemCount,
        BigDecimal grossRevenue,
        BigDecimal commission,
        BigDecimal cashTotal,
        BigDecimal checkTotal,
        BigDecimal cardTotal) {
}
