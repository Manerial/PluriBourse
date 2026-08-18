package org.pluribourse.domain.report.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.pluribourse.domain.edition.service.EditionService;
import org.pluribourse.domain.report.dto.DailySalesReportDto;
import org.pluribourse.domain.report.dto.EditionSummaryReportDto;
import org.pluribourse.domain.report.service.DailySalesReportPrintService;
import org.pluribourse.domain.report.service.EditionSummaryReportPrintService;
import org.pluribourse.domain.report.service.ReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final EditionSummaryReportPrintService editionSummaryReportPrintService;

    @GetMapping("/daily")
    public ResponseEntity<DailySalesReportDto> getDailyReport() {
        return ResponseEntity.ok(reportService.getDailyReport(editionService.getActiveEdition()));
    }

    @PostMapping("/daily/print")
    public ResponseEntity<Void> printDailyReport(HttpSession session) {
        dailySalesReportPrintService.printDailyReport(session);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/edition/{editionId}")
    public ResponseEntity<EditionSummaryReportDto> getEditionReport(@PathVariable Long editionId) {
        return ResponseEntity.ok(reportService.getEditionReport(editionService.requireEdition(editionId)));
    }

    @PostMapping("/edition/{editionId}/print")
    public ResponseEntity<Void> printEditionReport(@PathVariable Long editionId, HttpSession session) {
        editionSummaryReportPrintService.printEditionReport(editionId, session);
        return ResponseEntity.noContent().build();
    }
}
