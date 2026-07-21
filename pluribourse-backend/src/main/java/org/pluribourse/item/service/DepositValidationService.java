package org.pluribourse.item.service;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.pluribourse.edition.entity.Edition;
import org.pluribourse.edition.service.EditionService;
import org.pluribourse.item.entity.Item;
import org.pluribourse.item.exception.EmptyDepositException;
import org.pluribourse.item.repository.ItemRepository;
import org.pluribourse.print.entity.PrinterType;
import org.pluribourse.print.exception.InvalidPrinterSelectionException;
import org.pluribourse.print.service.PrintQueueService;
import org.pluribourse.print.service.PrinterSelectionService;
import org.pluribourse.print.service.ThermalPrintService;
import org.pluribourse.seller.entity.SellerProfile;
import org.pluribourse.user.enums.Language;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Single entry point for "valider le dépôt" (FR-028) — the point of extension for story 3.6, which
 * will add its own PDF job submission here once it exists. This story only queues the thermal
 * labels job; it deliberately does not persist any "deposit validated" state (see story Dev Notes
 * § Scope) — every call reprints every article currently registered for the seller.
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

    @Transactional(readOnly = true)
    public void validateDeposit(Long sellerProfileId, HttpSession session) {
        Edition edition = editionService.getActiveEdition();
        PhaseGuard.requireDepositPhase(edition);
        SellerProfile sellerProfile = editionScopedLookup.findSellerInEdition(sellerProfileId, edition);
        List<Item> items = itemRepository.findAllBySellerProfileIdOrderByItemNumberAsc(sellerProfile.getId());
        if (items.isEmpty()) {
            throw new EmptyDepositException(sellerProfileId);
        }

        Long printerId = printerSelectionService.getSelectedPrinterId(session, PrinterType.THERMAL)
                .orElseThrow(() -> new InvalidPrinterSelectionException("No thermal printer selected in session"));
        if (!printQueueService.isAvailable(printerId)) {
            throw new InvalidPrinterSelectionException("Selected thermal printer is not currently available");
        }

        Locale documentLocale = edition.getDocumentLanguage() == Language.FR ? Locale.FRENCH : Locale.ENGLISH;
        printQueueService.submit(printerId, thermalPrintService.buildDepositJob(sellerProfile, items, documentLocale));
    }
}
