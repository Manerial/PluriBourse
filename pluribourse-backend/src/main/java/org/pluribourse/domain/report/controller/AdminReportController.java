package org.pluribourse.domain.report.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.pluribourse.domain.edition.service.EditionService;
import org.pluribourse.domain.report.dto.DailySalesReportDto;
import org.pluribourse.domain.report.service.DailySalesReportPrintService;
import org.pluribourse.domain.report.service.ReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminReportController {

    private final EditionService editionService;
    private final ReportService reportService;
    private final DailySalesReportPrintService dailySalesReportPrintService;

    @GetMapping("/daily")
    public ResponseEntity<DailySalesReportDto> getDailyReport() {
        return ResponseEntity.ok(reportService.getDailyReport(editionService.getActiveEdition()));
    }

    @PostMapping("/daily/print")
    public ResponseEntity<Void> printDailyReport(HttpSession session) {
        dailySalesReportPrintService.printDailyReport(session);
        return ResponseEntity.noContent().build();
    }
}
