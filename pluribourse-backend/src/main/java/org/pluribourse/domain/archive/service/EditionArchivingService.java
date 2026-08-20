package org.pluribourse.domain.archive.service;

import lombok.RequiredArgsConstructor;
import org.pluribourse.domain.archive.entity.ArchivedItem;
import org.pluribourse.domain.archive.exception.EditionAlreadyArchivedException;
import org.pluribourse.domain.archive.exception.EditionNotClosedException;
import org.pluribourse.domain.archive.exception.NoItemsToArchiveException;
import org.pluribourse.domain.archive.repository.ArchivedItemRepository;
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

        List<Item> items = itemRepository.findAllByEditionIdForSettlementReport(id);
        if (items.isEmpty()) {
            throw new NoItemsToArchiveException();
        }

        // Snapshot before deleting anything (AC 7) — nothing's deleted yet, so this still resolves live.
        EditionSummaryReportDto snapshot = reportService.getEditionReport(edition);
        applySnapshot(edition, snapshot);

        List<ArchivedItem> archivedItems = items.stream().map(item -> {
            ArchivedItem archivedItem = new ArchivedItem();
            archivedItem.setEdition(edition);
            archivedItem.setName(item.getName());
            archivedItem.setCategoryName(item.getCategory().getName());
            archivedItem.setSold(item.isSold());
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
                saved.getStartDate(), saved.getEndDate(), null);
    }

    private void applySnapshot(Edition edition, EditionSummaryReportDto snapshot) {
        edition.setArchivedSoldItemCount(snapshot.soldItemCount());
        edition.setArchivedUnsoldItemCount(snapshot.unsoldItemCount());
        edition.setArchivedGrossRevenue(snapshot.grossRevenue());
        edition.setArchivedCommission(snapshot.commission());
        edition.setArchivedCashTotal(snapshot.cashTotal());
        edition.setArchivedCheckTotal(snapshot.checkTotal());
        edition.setArchivedCardTotal(snapshot.cardTotal());
        edition.setArchivedNetPayoutTotal(snapshot.netPayoutTotal());
        edition.setArchivedAssociationRevenueTotal(snapshot.associationRevenueTotal());
    }
}
