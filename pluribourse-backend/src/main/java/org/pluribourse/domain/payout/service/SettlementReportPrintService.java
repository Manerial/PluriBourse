package org.pluribourse.domain.payout.service;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pluribourse.domain.edition.entity.Edition;
import org.pluribourse.domain.edition.service.EditionService;
import org.pluribourse.domain.item.entity.Item;
import org.pluribourse.domain.item.repository.ItemRepository;
import org.pluribourse.domain.item.service.PhaseGuard;
import org.pluribourse.domain.payout.dto.BulkSettlementReportPrintResultDto;
import org.pluribourse.domain.payout.dto.SettlementFilter;
import org.pluribourse.domain.print.entity.PrinterType;
import org.pluribourse.domain.print.exception.InvalidPrinterSelectionException;
import org.pluribourse.domain.print.exception.PrinterNotFoundException;
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
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Story 5.2 — prints the per-seller sales report PDF ("bilan de vente", FR-050). Kept separate
 * from {@link SettlementService}, which stays dedicated to the settle/unclaimed flow (story 5.1) —
 * same separation as {@code PosInvoicePrintService}/{@code PosBasketService} (story 4.5). Reuses
 * {@link SettlementService#requireSellerOfEdition} rather than duplicating the IDOR guard.
 */
@Service
@RequiredArgsConstructor
@Slf4j
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
        List<Item> items = itemRepository.findAllBySellerProfileIdForSettlementReport(sellerId);
        BigDecimal amountPaid = settlementService.getAmountPaid(sellerId);

        PrintContext context = resolvePrintContext(edition, session);

        printQueueService.submit(context.a4PrinterId(),
                documentPrintService.buildSettlementReportJob(seller, items, context.commissionRate(), context.documentLocale(), amountPaid));
    }

    /**
     * Bulk settlement report printing (story 5.6, FR-097): queues one report per seller matching
     * {@code filter}, resolved server-side (the client is never the source of truth for which
     * sellers are targeted). The per-seller {@code try/catch} is what guarantees AC 5 ("jobs
     * already queued successfully are not cancelled") — {@link PrintQueueService#submit} only
     * ever fails on the narrow race where the printer is unregistered between the
     * {@code isAvailable} check above and this iteration ({@code PrinterNotFoundException}), but a
     * failure on one seller must never abort the loop for the rest. The seller id (never a name,
     * email or phone number, per CLAUDE.md) is logged so a failure can be diagnosed after the
     * fact — the client only ever sees {@code failedCount}.
     */
    @Transactional(readOnly = true)
    public BulkSettlementReportPrintResultDto printAllReports(SettlementFilter filter, HttpSession session) {
        Edition edition = editionService.getActiveEdition();
        PhaseGuard.requirePostSalePhase(edition);

        PrintContext context = resolvePrintContext(edition, session);

        List<SellerProfile> sellers = settlementService.getSellersMatchingFilter(edition, filter);
        Map<Long, List<Item>> itemsBySellerId = itemRepository.findAllByEditionIdForSettlementReport(edition.getId()).stream()
                .collect(Collectors.groupingBy(i -> i.getSellerProfile().getId()));
        Map<Long, BigDecimal> amountPaidBySellerId = settlementService.getAmountPaidBySellerId(edition);

        int succeeded = 0;
        int failed = 0;
        for (SellerProfile seller : sellers) {
            try {
                List<Item> items = itemsBySellerId.getOrDefault(seller.getId(), List.of());
                BigDecimal amountPaid = amountPaidBySellerId.get(seller.getId());
                printQueueService.submit(context.a4PrinterId(),
                        documentPrintService.buildSettlementReportJob(seller, items, context.commissionRate(), context.documentLocale(), amountPaid));
                succeeded++;
            } catch (PrinterNotFoundException e) {
                log.warn("Failed to queue settlement report for seller {}: {}", seller.getId(), e.getMessage());
                failed++;
            }
        }
        return new BulkSettlementReportPrintResultDto(succeeded, failed);
    }

    /**
     * Resolves and validates the session's selected A4 printer, plus the edition-derived
     * commission rate and document locale — the fixed context every settlement report job needs,
     * shared by {@link #printReport} and {@link #printAllReports}.
     */
    private PrintContext resolvePrintContext(Edition edition, HttpSession session) {
        Long a4PrinterId = printerSelectionService.getSelectedPrinterId(session, PrinterType.A4)
                .orElseThrow(() -> new InvalidPrinterSelectionException("No A4 printer selected in session"));
        if (!printQueueService.isAvailable(a4PrinterId)) {
            throw new InvalidPrinterSelectionException("Selected A4 printer is not currently available");
        }
        BigDecimal commissionRate = edition.getCommissionRate();
        Locale documentLocale = edition.getDocumentLanguage() == Language.FR ? Locale.FRENCH : Locale.ENGLISH;
        return new PrintContext(a4PrinterId, commissionRate, documentLocale);
    }

    private record PrintContext(Long a4PrinterId, BigDecimal commissionRate, Locale documentLocale) {
    }
}
