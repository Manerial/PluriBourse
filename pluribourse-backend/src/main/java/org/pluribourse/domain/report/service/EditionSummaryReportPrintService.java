package org.pluribourse.domain.report.service;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pluribourse.domain.edition.entity.Edition;
import org.pluribourse.domain.edition.service.EditionService;
import org.pluribourse.domain.print.entity.PrinterType;
import org.pluribourse.domain.print.exception.InvalidPrinterSelectionException;
import org.pluribourse.domain.print.exception.PrinterNotFoundException;
import org.pluribourse.domain.print.service.DocumentPrintService;
import org.pluribourse.domain.print.service.PrintQueueService;
import org.pluribourse.domain.print.service.PrinterSelectionService;
import org.pluribourse.domain.report.dto.EditionSummaryReportDto;
import org.pluribourse.domain.user.enums.Language;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Story 5.4 — prints the edition-wide summary report PDF ("bilan d'édition", FR-055). Same
 * pattern as {@link DailySalesReportPrintService} (story 5.3), but resolves the edition by
 * explicit ID via {@link EditionService#requireEdition} rather than {@code getActiveEdition()},
 * so it stays correct once the edition is Clôturée (see story 5.4 Dev Notes § Écarts).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EditionSummaryReportPrintService {

    private final EditionService editionService;
    private final ReportService reportService;
    private final PrinterSelectionService printerSelectionService;
    private final PrintQueueService printQueueService;
    private final DocumentPrintService documentPrintService;

    @Transactional(readOnly = true)
    public void printEditionReport(Long editionId, HttpSession session) {
        Edition edition = editionService.requireEdition(editionId);
        EditionSummaryReportDto report = reportService.getEditionReport(edition); // phase guard already applied inside
        String editionName = edition.getName();
        Locale documentLocale = edition.getDocumentLanguage() == Language.FR ? Locale.FRENCH : Locale.ENGLISH;

        Long a4PrinterId = printerSelectionService.getSelectedPrinterId(session, PrinterType.A4)
                .orElseThrow(() -> new InvalidPrinterSelectionException("No A4 printer selected in session"));
        if (!printQueueService.isAvailable(a4PrinterId)) {
            throw new InvalidPrinterSelectionException("Selected A4 printer is not currently available");
        }

        printQueueService.submit(a4PrinterId, documentPrintService.buildEditionReportJob(editionName, report, documentLocale));
    }

    /**
     * Edition closure (story 2.7, FR-013): unlike {@link #printEditionReport}, hardcodes both
     * locales instead of reading {@link Edition#getDocumentLanguage()} — the closure PDF is always
     * generated in EN+FR. Safe to call right after AC 3's phase transition commits:
     * {@code reportService.getEditionReport} already works in CLOSED
     * ({@link org.pluribourse.domain.item.service.PhaseGuard#requirePostSaleOrClosedPhase}).
     * <p>
     * The two submissions are independent, same as {@code SettlementReportPrintService#printAllReports}'s
     * per-seller loop: a {@link PrinterNotFoundException} on one locale must not discard a job
     * already queued for the other. Only throws once both languages have failed to submit.
     */
    @Transactional(readOnly = true)
    public void printEditionReportBothLanguages(Long editionId, HttpSession session) {
        Edition edition = editionService.requireEdition(editionId);
        EditionSummaryReportDto report = reportService.getEditionReport(edition);
        String editionName = edition.getName();

        Long a4PrinterId = printerSelectionService.getSelectedPrinterId(session, PrinterType.A4)
                .orElseThrow(() -> new InvalidPrinterSelectionException("No A4 printer selected in session"));
        if (!printQueueService.isAvailable(a4PrinterId)) {
            throw new InvalidPrinterSelectionException("Selected A4 printer is not currently available");
        }

        boolean frenchSubmitted = trySubmit(a4PrinterId, editionName, report, Locale.FRENCH);
        boolean englishSubmitted = trySubmit(a4PrinterId, editionName, report, Locale.ENGLISH);
        if (!frenchSubmitted && !englishSubmitted) {
            throw new PrinterNotFoundException(a4PrinterId);
        }
    }

    private boolean trySubmit(Long printerId, String editionName, EditionSummaryReportDto report, Locale locale) {
        try {
            printQueueService.submit(printerId, documentPrintService.buildEditionReportJob(editionName, report, locale));
            return true;
        } catch (PrinterNotFoundException e) {
            log.warn("Failed to queue edition closure report ({}) for printer {}: {}", locale, printerId, e.getMessage());
            return false;
        }
    }
}
