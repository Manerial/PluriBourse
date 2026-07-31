package org.pluribourse.domain.item.service;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.pluribourse.domain.edition.entity.Edition;
import org.pluribourse.domain.edition.service.EditionService;
import org.pluribourse.domain.item.entity.Item;
import org.pluribourse.domain.item.exception.EmptyDepositException;
import org.pluribourse.domain.item.repository.ItemRepository;
import org.pluribourse.domain.print.entity.PrinterType;
import org.pluribourse.domain.print.exception.InvalidPrinterSelectionException;
import org.pluribourse.domain.print.service.DocumentPrintService;
import org.pluribourse.domain.print.service.PrintJob;
import org.pluribourse.domain.print.service.PrintQueueService;
import org.pluribourse.domain.print.service.PrinterSelectionService;
import org.pluribourse.domain.print.service.ThermalPrintService;
import org.pluribourse.domain.seller.entity.SellerProfile;
import org.pluribourse.domain.user.enums.Language;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Two independent print actions available from the seller's deposit page, reachable from the
 * Deposit and Post-sale phases (FR-031): (re)printing the thermal labels (FR-028, story 3.5) and
 * (re)printing the deposit slip PDF (story 3.6). Deliberately not persisting any "deposit
 * validated" state (see story 3.5 Dev Notes § Scope) — every call reprints every article
 * currently registered for the seller, and each action only checks the printer it actually needs.
 */
@Service
@RequiredArgsConstructor
public class DepositValidationService {

    private final EditionService editionService;
    private final EditionScopedLookup editionScopedLookup;
    private final ItemRepository itemRepository;
    private final PrinterSelectionService printerSelectionService;
    private final PrintQueueService printQueueService;
    private final ThermalPrintService thermalPrintService;
    private final DocumentPrintService documentPrintService;

    @Transactional(readOnly = true)
    public void reprintLabels(Long sellerProfileId, HttpSession session) {
        Edition edition = editionService.getActiveEdition();
        PhaseGuard.requireDepositOrPostSalePhase(edition);
        SellerProfile sellerProfile = editionScopedLookup.findSellerInEdition(sellerProfileId, edition);
        List<Item> items = itemRepository.findAllBySellerProfileIdOrderByItemNumberAsc(sellerProfile.getId());
        if (items.isEmpty()) {
            throw new EmptyDepositException(sellerProfileId);
        }

        Long thermalPrinterId = printerSelectionService.getSelectedPrinterId(session, PrinterType.THERMAL)
                .orElseThrow(() -> new InvalidPrinterSelectionException("No thermal printer selected in session"));
        if (!printQueueService.isAvailable(thermalPrinterId)) {
            throw new InvalidPrinterSelectionException("Selected thermal printer is not currently available");
        }

        Locale documentLocale = resolveDocumentLocale(edition);
        printQueueService.submit(thermalPrinterId, thermalPrintService.buildDepositJob(sellerProfile, items, documentLocale));
    }

    @Transactional(readOnly = true)
    public void reprintDepositSlip(Long sellerProfileId, HttpSession session) {
        Edition edition = editionService.getActiveEdition();
        PhaseGuard.requireDepositOrPostSalePhase(edition);
        SellerProfile sellerProfile = editionScopedLookup.findSellerInEdition(sellerProfileId, edition);
        List<Item> items = itemRepository.findAllBySellerProfileIdOrderByItemNumberAsc(sellerProfile.getId());
        if (items.isEmpty()) {
            throw new EmptyDepositException(sellerProfileId);
        }

        Long a4PrinterId = printerSelectionService.getSelectedPrinterId(session, PrinterType.A4)
                .orElseThrow(() -> new InvalidPrinterSelectionException("No A4 printer selected in session"));
        if (!printQueueService.isAvailable(a4PrinterId)) {
            throw new InvalidPrinterSelectionException("Selected A4 printer is not currently available");
        }

        Locale documentLocale = resolveDocumentLocale(edition);
        printQueueService.submit(a4PrinterId, buildDepositSlipJob(sellerProfile, items, edition, documentLocale));
    }

    private PrintJob buildDepositSlipJob(SellerProfile sellerProfile, List<Item> items, Edition edition, Locale documentLocale) {
        return documentPrintService.buildDepositSlipJob(sellerProfile, items, edition.getCommissionRate(), documentLocale);
    }

    private Locale resolveDocumentLocale(Edition edition) {
        return edition.getDocumentLanguage() == Language.FR ? Locale.FRENCH : Locale.ENGLISH;
    }
}
