package org.pluribourse.domain.payout.service;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.pluribourse.domain.edition.entity.Edition;
import org.pluribourse.domain.edition.service.EditionService;
import org.pluribourse.domain.item.entity.Item;
import org.pluribourse.domain.item.repository.ItemRepository;
import org.pluribourse.domain.item.service.PhaseGuard;
import org.pluribourse.domain.print.entity.PrinterType;
import org.pluribourse.domain.print.exception.InvalidPrinterSelectionException;
import org.pluribourse.domain.print.service.DocumentPrintService;
import org.pluribourse.domain.print.service.PrintQueueService;
import org.pluribourse.domain.print.service.PrinterSelectionService;
import org.pluribourse.domain.seller.entity.SellerProfile;
import org.pluribourse.domain.user.enums.Language;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * Story 5.2 — prints the per-seller sales report PDF ("bilan de vente", FR-050). Kept separate
 * from {@link SettlementService}, which stays dedicated to the settle/unclaimed flow (story 5.1) —
 * same separation as {@code PosInvoicePrintService}/{@code PosBasketService} (story 4.5). Reuses
 * {@link SettlementService#requireSellerOfEdition} rather than duplicating the IDOR guard.
 */
@Service
@RequiredArgsConstructor
public class SettlementReportPrintService {

    private final ItemRepository itemRepository;
    private final EditionService editionService;
    private final SettlementService settlementService;
    private final PrinterSelectionService printerSelectionService;
    private final PrintQueueService printQueueService;
    private final DocumentPrintService documentPrintService;

    @Transactional(readOnly = true)
    public void printReport(Long sellerId, HttpSession session) {
        Edition edition = editionService.getActiveEdition();
        // Defense in depth: /volunteer|admin/settlement is already phase-gated client-side, but the
        // client is never trusted alone (same rationale as the rest of SettlementService).
        PhaseGuard.requirePostSalePhase(edition);
        SellerProfile seller = settlementService.requireSellerOfEdition(sellerId, edition);
        BigDecimal commissionRate = edition.getCommissionRate();
        Locale documentLocale = edition.getDocumentLanguage() == Language.FR ? Locale.FRENCH : Locale.ENGLISH;

        List<Item> items = itemRepository.findAllBySellerProfileIdForSettlementReport(sellerId);

        Long a4PrinterId = printerSelectionService.getSelectedPrinterId(session, PrinterType.A4)
                .orElseThrow(() -> new InvalidPrinterSelectionException("No A4 printer selected in session"));
        if (!printQueueService.isAvailable(a4PrinterId)) {
            throw new InvalidPrinterSelectionException("Selected A4 printer is not currently available");
        }

        printQueueService.submit(a4PrinterId,
                documentPrintService.buildSettlementReportJob(seller, items, commissionRate, documentLocale));
    }
}
