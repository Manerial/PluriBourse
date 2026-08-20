package org.pluribourse.domain.payout.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.pluribourse.domain.payout.dto.BulkSettlementReportPrintResultDto;
import org.pluribourse.domain.payout.dto.SettlementFilter;
import org.pluribourse.domain.payout.service.SettlementReportPrintService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only settlement actions (story 5.6, AC 7) — {@link SettlementController} (/settlements)
 * stays shared ADMIN+VOLUNTEER without any {@code @PreAuthorize} (story 5.1). Same dedicated
 * sibling-controller pattern as {@code SellerController}/{@code AdminSellerController} rather
 * than a method-level {@code @PreAuthorize} on the shared controller.
 */
@RestController
@RequestMapping("/admin/settlements")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminSettlementController {

    private final SettlementReportPrintService reportPrintService;

    @PostMapping("/report/print-all")
    public ResponseEntity<BulkSettlementReportPrintResultDto> printAllReports(@RequestParam SettlementFilter filter, HttpSession session) {
        return ResponseEntity.ok(reportPrintService.printAllReports(filter, session));
    }
}
