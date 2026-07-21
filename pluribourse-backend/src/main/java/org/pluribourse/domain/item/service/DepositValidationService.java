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
 * Single entry point for "valider le dépôt" (FR-028): queues the thermal labels job (story 3.5)
 * and the deposit slip PDF job (story 3.6) in the same call, deliberately not persisting any
 * "deposit validated" state (see story 3.5 Dev Notes § Scope) — every call reprints every article
 * currently registered for the seller. Also the entry point for reprinting the deposit slip alone
 * (FR-031), reachable from the Deposit and Post-sale phases.
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
    public void validateDeposit(Long sellerProfileId, HttpSession session) {
        Edition edition = editionService.getActiveEdition();
        PhaseGuard.requireDepositPhase(edition);
        SellerProfile sellerProfile = editionScopedLookup.findSellerInEdition(sellerProfileId, edition);
        List<Item> items = itemRepository.findAllBySellerProfileIdOrderByItemNumberAsc(sellerProfile.getId());
        if (items.isEmpty()) {
            throw new EmptyDepositException(sellerProfileId);
        }

        // Both printer selections are resolved and validated before either job is submitted
        // (AC2): a thermal roll must never be printed if the slip cannot follow, and vice versa —
        // once a job is submitted it cannot be cancelled.
        Long thermalPrinterId = printerSelectionService.getSelectedPrinterId(session, PrinterType.THERMAL)
                .orElseThrow(() -> new InvalidPrinterSelectionException("No thermal printer selected in session"));
        if (!printQueueService.isAvailable(thermalPrinterId)) {
            throw new InvalidPrinterSelectionException("Selected thermal printer is not currently available");
        }
        Long a4PrinterId = printerSelectionService.getSelectedPrinterId(session, PrinterType.A4)
                .orElseThrow(() -> new InvalidPrinterSelectionException("No A4 printer selected in session"));
        if (!printQueueService.isAvailable(a4PrinterId)) {
            throw new InvalidPrinterSelectionException("Selected A4 printer is not currently available");
        }

        Locale documentLocale = resolveDocumentLocale(edition);
        printQueueService.submit(thermalPrinterId, thermalPrintService.buildDepositJob(sellerProfile, items, documentLocale));
        printQueueService.submit(a4PrinterId, buildDepositSlipJob(sellerProfile, items, edition, documentLocale));
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
