package org.pluribourse.domain.report.service;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.pluribourse.domain.edition.entity.Edition;
import org.pluribourse.domain.edition.service.EditionService;
import org.pluribourse.domain.print.entity.PrinterType;
import org.pluribourse.domain.print.exception.InvalidPrinterSelectionException;
import org.pluribourse.domain.print.service.DocumentPrintService;
import org.pluribourse.domain.print.service.PrintQueueService;
import org.pluribourse.domain.print.service.PrinterSelectionService;
import org.pluribourse.domain.report.dto.DailySalesReportDto;
import org.pluribourse.domain.user.enums.Language;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Story 5.3 — prints the daily sales report PDF ("bilan des ventes journalier", FR-054). Same
 * pattern as {@code SettlementReportPrintService} (story 5.2): kept separate from the read-only
 * {@link ReportService}, which computes the report data reused by both the screen view and this
 * print path.
 */
@Service
@RequiredArgsConstructor
public class DailySalesReportPrintService {

    private final EditionService editionService;
    private final ReportService reportService;
    private final PrinterSelectionService printerSelectionService;
    private final PrintQueueService printQueueService;
    private final DocumentPrintService documentPrintService;

    @Transactional(readOnly = true)
    public void printDailyReport(HttpSession session) {
        Edition edition = editionService.getActiveEdition();
        DailySalesReportDto report = reportService.getDailyReport(edition); // phase guard already applied inside
        String editionName = edition.getName();
        Locale documentLocale = edition.getDocumentLanguage() == Language.FR ? Locale.FRENCH : Locale.ENGLISH;

        Long a4PrinterId = printerSelectionService.getSelectedPrinterId(session, PrinterType.A4)
                .orElseThrow(() -> new InvalidPrinterSelectionException("No A4 printer selected in session"));
        if (!printQueueService.isAvailable(a4PrinterId)) {
            throw new InvalidPrinterSelectionException("Selected A4 printer is not currently available");
        }

        printQueueService.submit(a4PrinterId, documentPrintService.buildDailyReportJob(editionName, report, documentLocale));
    }
}
