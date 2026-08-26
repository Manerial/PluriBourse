package org.pluribourse.domain.item.service;

import lombok.*;
import org.pluribourse.domain.edition.entity.*;
import org.pluribourse.domain.edition.service.*;
import org.pluribourse.domain.item.dto.*;
import org.pluribourse.domain.item.entity.*;
import org.pluribourse.domain.item.exception.*;
import org.pluribourse.domain.item.mapper.*;
import org.pluribourse.domain.item.repository.*;
import org.pluribourse.domain.seller.entity.*;
import org.pluribourse.domain.seller.exception.*;
import org.pluribourse.domain.seller.repository.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.*;

import java.util.*;
import java.util.stream.*;

@Service
@RequiredArgsConstructor
public class LotService {

    private final LotRepository repository;
    private final ItemRepository itemRepository;
    private final EditionScopedLookup editionScopedLookup;
    private final EditionService editionService;
    private final TableAssignmentService tableAssignmentService;
    private final SellerRepository sellerRepository;
    private final ItemMapper itemMapper;

    @Transactional
    public LotDto create(CreateLotDto dto) {
        Edition edition = editionService.getActiveEdition();
        PhaseGuard.requireDepositPhase(edition);
        SellerProfile sellerProfile = editionScopedLookup.findSellerInEdition(dto.sellerProfileId(), edition);
        EditionCategory category = editionScopedLookup.findCategoryInEdition(dto.categoryId(), edition);

        Lot lot = new Lot();
        lot.setEdition(edition);
        lot.setSellerProfile(sellerProfile);
        lot.setCategory(category);
        lot.setName(dto.name());
        lot.setGlobalPrice(dto.globalPrice());
        lot = repository.save(lot);

        // Lock the seller once for the whole lot (FR-026), then assign ONE shared table for every
        // member from the lot's single category (FR-023) — same lock ordering as before (seller,
        // then category) but only ever one category per lot now, so no cross-lot deadlock ordering
        // is needed anymore.
        SellerProfile lockedSeller = sellerRepository.lockById(sellerProfile.getId())
                .orElseThrow(() -> new SellerNotFoundException(sellerProfile.getId()));
        int nextItemNumber = lockedSeller.getNextItemNumber();
        int tableNumber = tableAssignmentService.assignTable(sellerProfile, category, edition);

        Item[] createdItems = new Item[dto.items().size()];
        for (int i = 0; i < dto.items().size(); i++) {
            CreateLotItemDto itemDto = dto.items().get(i);
            if (nextItemNumber > Item.MAX_BARCODE_SEGMENT) {
                throw new TooManyItemsException(sellerProfile.getId());
            }
            Item item = new Item();
            item.setEdition(edition);
            item.setSellerProfile(sellerProfile);
            item.setCategory(category);
            item.setLot(lot);
            item.setName(itemDto.name());
            item.setIncomplete(itemDto.incomplete());
            item.setComment(itemDto.comment());
            item.setTableNumber(tableNumber);
            item.setItemNumber(nextItemNumber++);
            createdItems[i] = itemRepository.save(item);
        }
        lockedSeller.setNextItemNumber(nextItemNumber);

        return new LotDto(lot.getId(), lot.getName(), lot.getGlobalPrice(), category.getId(), category.getName(),
                itemMapper.toDtos(Arrays.asList(createdItems)));
    }

    /**
     * Reconciles a lot's members against {@code dto.items()}: entries with a non-null {@code id}
     * update an existing member; entries with a null {@code id} create new members; any current
     * member absent from the submitted list is deleted outright (a lot item has no price of its
     * own, so there is no valid data to detach it as a standalone individual item). A lot has ONE
     * category shared by every member (FR-022/FR-023) — reassigning it moves the whole lot's shared
     * table in a single operation, never per item. The seller row is locked only when at least one
     * new item is added, since that is the only case that consumes the shared {@code nextItemNumber}
     * counter (FR-026).
     */
    @Transactional
    public LotDto update(Long lotId, UpdateLotDto dto) {
        Lot lot = repository.findById(lotId).orElseThrow(() -> new LotNotFoundException(lotId));
        Edition edition = lot.getEdition();
        PhaseGuard.requireDepositPhase(edition);
        SellerProfile sellerProfile = lot.getSellerProfile();
        EditionCategory category = editionScopedLookup.findCategoryInEdition(dto.categoryId(), edition);

        List<Item> currentMembers = lot.getItems();
        Map<Long, Item> currentById = currentMembers.stream().collect(Collectors.toMap(Item::getId, item -> item));

        List<Integer> updateIndexes = new ArrayList<>();
        List<Integer> newIndexes = new ArrayList<>();
        Set<Long> seenIds = new HashSet<>();
        for (int i = 0; i < dto.items().size(); i++) {
            UpdateLotItemDto itemDto = dto.items().get(i);
            if (itemDto.id() != null) {
                if (!currentById.containsKey(itemDto.id())) {
                    throw new ItemNotFoundException(itemDto.id());
                }
                if (!seenIds.add(itemDto.id())) {
                    throw new DuplicateLotItemException(itemDto.id());
                }
                updateIndexes.add(i);
            } else {
                newIndexes.add(i);
            }
        }

        // Any current member whose id is absent from the submitted list is removed for good — a
        // lot item never has a price of its own, so there is no valid data to detach it as a
        // standalone individual item (see LotService Javadoc / story 3.10 Dev Notes). Removed via
        // currentMembers.removeIf rather than a plain for-loop so lot.getItems() (same reference)
        // stays in sync with the deletions — otherwise a later query (e.g. assignTable's lookups)
        // auto-flushes the pending deletes, and the final repository.save(lot) merge fails trying
        // to resolve now-nonexistent ids still sitting in the stale in-memory collection.
        Set<Long> submittedIds = updateIndexes.stream().map(i -> dto.items().get(i).id()).collect(Collectors.toSet());
        currentMembers.removeIf(member -> {
            if (submittedIds.contains(member.getId())) {
                return false;
            }
            itemRepository.delete(member);
            return true;
        });

        // A lot has ONE category shared by every member (FR-022/FR-023, this story) — reassign the
        // shared table only when the category actually changes or a member is added, never per item.
        boolean categoryChanged = !lot.getCategory().getId().equals(category.getId());
        boolean hasNewItems = !newIndexes.isEmpty();
        // Stays null only when neither branch below runs — the two read sites (line ~162 and the
        // unconditional one in the newIndexes loop) are only ever reached when one of them did.
        Integer sharedTableNumber = null;
        if (categoryChanged) {
            // Excludes every remaining current member (still sitting on the OLD table) so their own
            // rows never bias the recount of the NEW category's least-loaded table.
            Set<Long> remainingMemberIds = updateIndexes.stream().map(i -> dto.items().get(i).id()).collect(Collectors.toSet());
            sharedTableNumber = tableAssignmentService.assignTable(sellerProfile, category, edition, remainingMemberIds);
        } else if (hasNewItems) {
            // Category unchanged: the seller's existing members are still in it, so the normal
            // "already has a table in this category" lookup finds them directly — no exclusion.
            sharedTableNumber = tableAssignmentService.assignTable(sellerProfile, category, edition);
        }

        SellerProfile lockedSeller = hasNewItems
                ? sellerRepository.lockById(sellerProfile.getId()).orElseThrow(() -> new SellerNotFoundException(sellerProfile.getId()))
                : sellerProfile;
        int nextItemNumber = hasNewItems ? lockedSeller.getNextItemNumber() : 0;

        for (int i : updateIndexes) {
            UpdateLotItemDto itemDto = dto.items().get(i);
            Item item = currentById.get(itemDto.id());
            item.setName(itemDto.name());
            item.setIncomplete(itemDto.incomplete());
            item.setComment(itemDto.comment());
            if (categoryChanged) {
                item.setCategory(category);
                item.setTableNumber(sharedTableNumber);
            }
            itemRepository.save(item);
        }
        for (int i : newIndexes) {
            if (nextItemNumber > Item.MAX_BARCODE_SEGMENT) {
                throw new TooManyItemsException(sellerProfile.getId());
            }
            UpdateLotItemDto itemDto = dto.items().get(i);
            Item item = new Item();
            item.setEdition(edition);
            item.setSellerProfile(sellerProfile);
            item.setCategory(category);
            item.setLot(lot);
            item.setName(itemDto.name());
            item.setIncomplete(itemDto.incomplete());
            item.setComment(itemDto.comment());
            item.setTableNumber(sharedTableNumber);
            item.setItemNumber(nextItemNumber++);
            itemRepository.save(item);
        }
        if (hasNewItems) {
            lockedSeller.setNextItemNumber(nextItemNumber);
        }

        lot.setCategory(category);
        lot.setName(dto.name());
        lot.setGlobalPrice(dto.globalPrice());
        repository.save(lot);

        return new LotDto(lot.getId(), lot.getName(), lot.getGlobalPrice(), category.getId(), category.getName(),
                itemMapper.toDtos(itemRepository.findAllByLotIdOrderById(lot.getId())));
    }

    /**
     * Deletes every member item before the lot itself — {@code fk_items_lot} (015-lots.xml) has no
     * delete cascade, so deleting the lot first would violate the foreign key.
     */
    @Transactional
    public void delete(Long lotId) {
        Lot lot = repository.findById(lotId).orElseThrow(() -> new LotNotFoundException(lotId));
        PhaseGuard.requireDepositPhase(lot.getEdition());
        // fk_items_lot has no delete cascade (015-lots.xml) — members must be deleted first.
        itemRepository.deleteAll(lot.getItems());
        repository.delete(lot);
    }
}
