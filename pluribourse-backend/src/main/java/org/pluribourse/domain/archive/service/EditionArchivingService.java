package org.pluribourse.domain.archive.service;

import lombok.RequiredArgsConstructor;
import org.pluribourse.domain.archive.entity.ArchivedItem;
import org.pluribourse.domain.archive.entity.EditionArchiveSnapshot;
import org.pluribourse.domain.archive.exception.EditionAlreadyArchivedException;
import org.pluribourse.domain.archive.exception.EditionNotClosedException;
import org.pluribourse.domain.archive.repository.ArchivedItemRepository;
import org.pluribourse.domain.archive.repository.EditionArchiveSnapshotRepository;
import org.pluribourse.domain.edition.dto.EditionDto;
import org.pluribourse.domain.edition.entity.Edition;
import org.pluribourse.domain.edition.entity.PhaseType;
import org.pluribourse.domain.edition.repository.EditionRepository;
import org.pluribourse.domain.edition.service.EditionService;
import org.pluribourse.domain.item.entity.Item;
import org.pluribourse.domain.item.repository.ItemRepository;
import org.pluribourse.domain.payout.entity.Settlement;
import org.pluribourse.domain.payout.repository.SettlementRepository;
import org.pluribourse.domain.report.dto.EditionSummaryReportDto;
import org.pluribourse.domain.report.service.ReportService;
import org.pluribourse.domain.seller.entity.SellerProfile;
import org.pluribourse.domain.seller.repository.SellerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Edition archiving (story 2.7, AC 5/6/7, FR-088): a new orchestrating service, not new methods on
 * {@link EditionService} — {@link ReportService} already depends on {@code EditionService}, so
 * adding the reverse dependency there would create a circular Spring bean dependency (same
 * rationale as {@link org.pluribourse.domain.edition.service.EditionClosingService}).
 */
@Service
@RequiredArgsConstructor
public class EditionArchivingService {

    private final EditionService editionService;
    private final EditionRepository editionRepository;
    private final ItemRepository itemRepository;
    private final ArchivedItemRepository archivedItemRepository;
    private final EditionArchiveSnapshotRepository editionArchiveSnapshotRepository;
    private final SettlementRepository settlementRepository;
    private final SellerRepository sellerRepository;
    private final ReportService reportService;

    @Transactional
    public EditionDto archiveEdition(Long id) {
        Edition edition = editionService.requireEdition(id);
        if (edition.getPhase() != PhaseType.CLOSED) {
            throw new EditionNotClosedException();
        }
        if (edition.isArchived()) {
            throw new EditionAlreadyArchivedException();
        }

        // Follow-up fix (2026-08-23): archiving is no longer gated on having any items — a Clôturée
        // edition with zero deposited items (e.g. sellers registered but never deposited) still needs
        // a way to purge its seller profiles and freeze its (zero-valued) report snapshot. The 0-items
        // signal now surfaces earlier and non-blocking, as a warning on the DEPOSIT → SALE transition
        // (PhaseControlComponent), where an admin can still act on it.
        List<Item> items = itemRepository.findAllByEditionIdForSettlementReport(id);

        // Snapshot before deleting anything (AC 7) — nothing's deleted yet, so this still resolves live.
        EditionSummaryReportDto snapshot = reportService.getEditionReport(edition);
        applySnapshot(edition, snapshot);

        List<ArchivedItem> archivedItems = items.stream().map(item -> {
            ArchivedItem archivedItem = new ArchivedItem();
            archivedItem.setEdition(edition);
            archivedItem.setName(item.getName());
            archivedItem.setCategoryName(item.getCategory().getName());
            archivedItem.setSold(item.isSold());
            // A lot member's own price is null — its price lives on the lot itself
            // (ItemPricing's convention throughout, e.g. SettlementReportRenderer).
            archivedItem.setPrice(item.getLot() != null ? item.getLot().getGlobalPrice() : item.getPrice());
            return archivedItem;
        }).toList();
        archivedItemRepository.saveAll(archivedItems);

        itemRepository.deleteAll(items);

        // Settlements before sellers (see EditionArchivingService's story Dev Notes § FK ordering):
        // fk_settlements_seller_profile has no deleteCascade, and every seller has a Settlement row
        // once closed — SellerService.delete() would reject every one of them for exactly this reason.
        List<Settlement> settlements = settlementRepository.findAllBySellerProfileEditionId(id);
        settlementRepository.deleteAll(settlements);

        List<SellerProfile> sellers = sellerRepository.findAllByEditionId(id);
        sellerRepository.deleteAll(sellers);

        edition.setArchived(true);
        Edition saved = editionRepository.save(edition);

        // hasItems left null, same convention as advancePhase/rollbackPhase/closeEdition — only
        // EditionService.getEditionById populates it (story 2.7, T4).
        return new EditionDto(saved.getId(), saved.getName(), saved.getPhase(), saved.getCommissionRate(),
                saved.getDocumentLanguage(), saved.getCreatedAt(), saved.isArchived(),
                saved.getStartDate(), saved.getEndDate(), null, saved.getCurrency());
    }

    private void applySnapshot(Edition edition, EditionSummaryReportDto snapshot) {
        EditionArchiveSnapshot entity = new EditionArchiveSnapshot();
        entity.setEditionId(edition.getId());
        entity.setSoldItemCount(snapshot.soldItemCount());
        entity.setUnsoldItemCount(snapshot.unsoldItemCount());
        entity.setGrossRevenue(snapshot.grossRevenue());
        entity.setCommission(snapshot.commission());
        entity.setCashTotal(snapshot.cashTotal());
        entity.setCheckTotal(snapshot.checkTotal());
        entity.setCardTotal(snapshot.cardTotal());
        entity.setNetPayoutTotal(snapshot.netPayoutTotal());
        entity.setAssociationRevenueTotal(snapshot.associationRevenueTotal());
        editionArchiveSnapshotRepository.save(entity);
    }
}
