package org.pluribourse.domain.pos.service;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.pluribourse.domain.instanceconfig.service.GlobalInstanceConfigService;
import org.pluribourse.domain.item.entity.Item;
import org.pluribourse.domain.item.repository.ItemRepository;
import org.pluribourse.domain.pos.entity.Sale;
import org.pluribourse.domain.pos.exception.SaleNotFoundException;
import org.pluribourse.domain.pos.repository.SaleRepository;
import org.pluribourse.domain.print.entity.PrinterType;
import org.pluribourse.domain.print.exception.InvalidPrinterSelectionException;
import org.pluribourse.domain.print.service.DocumentPrintService;
import org.pluribourse.domain.print.service.PrintQueueService;
import org.pluribourse.domain.print.service.PrinterSelectionService;
import org.pluribourse.domain.user.enums.Language;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Story 4.5 — prints the buyer invoice PDF for an already-validated {@link Sale} (FR-041). Kept
 * separate from {@link PosBasketService}, which only ever deals with a {@code Basket} — by the
 * time a {@code Sale} exists, its originating basket has already been deleted (story 4.2). No
 * {@code PhaseGuard} here: unlike deposit items, a {@code Sale} is an immutable historical record
 * once created, and nothing in the epic conditions its invoice on the current edition phase.
 */
@Service
@RequiredArgsConstructor
public class PosInvoicePrintService {

    private final SaleRepository saleRepository;
    private final ItemRepository itemRepository;
    private final GlobalInstanceConfigService globalInstanceConfigService;
    private final PrinterSelectionService printerSelectionService;
    private final PrintQueueService printQueueService;
    private final DocumentPrintService documentPrintService;

    @Transactional(readOnly = true)
    public void printInvoice(Long saleId, Long userId, HttpSession session) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new SaleNotFoundException(saleId));
        if (!sale.getUser().getId().equals(userId)) {
            // Never distinguish "doesn't exist" from "belongs to someone else" (IDOR, AC 6), same
            // pattern as PosBasketService.requireOwnedBasket.
            throw new SaleNotFoundException(saleId);
        }

        // Extracted into plain values BEFORE building the job: the PrintJob executes on the print
        // queue's consumer thread, after this transaction has ended (Dev Notes § Chargement
        // eager). Capturing the Sale/Edition entity itself into the closure risks a real
        // LazyInitializationException in production — the same trap already hit twice on this
        // print module (stories 3.5/3.6).
        String editionName = sale.getEdition().getName();
        Locale documentLocale = sale.getEdition().getDocumentLanguage() == Language.FR ? Locale.FRENCH : Locale.ENGLISH;
        String associationName = globalInstanceConfigService.getConfig().associationName();
        String currency = sale.getEdition().getCurrency();
        LocalDateTime soldAt = sale.getSoldAt();

        List<Item> items = itemRepository.findAllBySaleIdOrderById(saleId);

        Long a4PrinterId = printerSelectionService.getSelectedPrinterId(session, PrinterType.A4)
                .orElseThrow(() -> new InvalidPrinterSelectionException("No A4 printer selected in session"));
        if (!printQueueService.isAvailable(a4PrinterId)) {
            throw new InvalidPrinterSelectionException("Selected A4 printer is not currently available");
        }

        printQueueService.submit(a4PrinterId,
                documentPrintService.buildInvoiceJob(associationName, editionName, soldAt, items, documentLocale, currency));
    }
}
