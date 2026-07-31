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

        // Resolve every category up-front, before persisting the lot or any item, so a single
        // invalid categoryId fails the whole request without taking pessimistic table-assignment
        // locks (TableAssignmentService) for items that will never be created.
        List<EditionCategory> categories = dto.items().stream()
                .map(item -> editionScopedLookup.findCategoryInEdition(item.categoryId(), edition))
                .toList();

        Lot lot = new Lot();
        lot.setEdition(edition);
        lot.setSellerProfile(sellerProfile);
        lot.setName(dto.name());
        lot.setGlobalPrice(dto.globalPrice());
        lot = repository.save(lot);

        // Lock the seller once for the whole lot (FR-026), not per item — a 5-item lot must not
        // acquire/release this lock 5 times. Same rationale as ItemService's single-item lock;
        // see SellerProfile.nextItemNumber for why a persisted counter is used instead of MAX+1.
        SellerProfile lockedSeller = sellerRepository.lockById(sellerProfile.getId())
                .orElseThrow(() -> new SellerNotFoundException(sellerProfile.getId()));
        int nextItemNumber = lockedSeller.getNextItemNumber();

        // Assign tables (and their underlying per-category pessimistic locks) in a globally
        // consistent order across requests — ascending category id — rather than the order items
        // happen to arrive in the payload. Otherwise two concurrent lots referencing the same pair
        // of categories in opposite orders can lock them in reverse order and deadlock at the DB.
        Item[] createdItems = new Item[dto.items().size()];
        List<Integer> lockOrder = IntStream.range(0, dto.items().size())
                .boxed()
                .sorted(Comparator.comparing(i -> categories.get(i).getId()))
                .toList();
        for (int i : lockOrder) {
            CreateLotItemDto itemDto = dto.items().get(i);
            EditionCategory category = categories.get(i);

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
            item.setTableNumber(tableAssignmentService.assignTable(sellerProfile, category, edition));
            item.setItemNumber(nextItemNumber++);
            createdItems[i] = itemRepository.save(item);
        }
        lockedSeller.setNextItemNumber(nextItemNumber);

        return new LotDto(lot.getId(), lot.getName(), lot.getGlobalPrice(), itemMapper.toDtos(Arrays.asList(createdItems)));
    }

    /**
     * Reconciles a lot's members against {@code dto.items()}: entries with a non-null {@code id}
     * update an existing member (its category may be reassigned, which moves its table per
     * FR-023); entries with a null {@code id} create new members; any current member absent from
     * the submitted list is deleted outright (a lot item has no price of its own, so there is no
     * valid data to detach it as a standalone individual item). The seller row is locked only when
     * at least one new item is added, since that is the only case that consumes the shared
     * {@code nextItemNumber} counter (FR-026).
     */
    @Transactional
    public LotDto update(Long lotId, UpdateLotDto dto) {
        Lot lot = repository.findById(lotId).orElseThrow(() -> new LotNotFoundException(lotId));
        Edition edition = lot.getEdition();
        PhaseGuard.requireDepositPhase(edition);
        SellerProfile sellerProfile = lot.getSellerProfile();

        // Resolve every category up-front, before any mutation, same fail-fast rationale as create().
        List<EditionCategory> categories = dto.items().stream()
                .map(item -> editionScopedLookup.findCategoryInEdition(item.categoryId(), edition))
                .toList();

        List<Item> currentMembers = lot.getItems();
        Map<Long, Item> currentById = currentMembers.stream().collect(Collectors.toMap(Item::getId, item -> item));

        // Partition submitted items: entries with a non-null id are updates to an existing member
        // (validated below to actually belong to this lot, and to appear at most once); entries
        // with a null id are new items.
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
        // standalone individual item (see LotService Javadoc / story 3.10 Dev Notes).
        Set<Long> submittedIds = updateIndexes.stream().map(i -> dto.items().get(i).id()).collect(Collectors.toSet());
        for (Item member : currentMembers) {
            if (!submittedIds.contains(member.getId())) {
                itemRepository.delete(member);
            }
        }

        // Separate updated members whose category actually changes (need a table reassignment,
        // hence a category lock) from those that do not (plain field update, no lock needed).
        List<Integer> categoryChangeIndexes = new ArrayList<>();
        List<Integer> noChangeIndexes = new ArrayList<>();
        for (int i : updateIndexes) {
            Item existing = currentById.get(dto.items().get(i).id());
            if (!existing.getCategory().getId().equals(categories.get(i).getId())) {
                categoryChangeIndexes.add(i);
            } else {
                noChangeIndexes.add(i);
            }
        }

        for (int i : noChangeIndexes) {
            UpdateLotItemDto itemDto = dto.items().get(i);
            Item item = currentById.get(itemDto.id());
            item.setName(itemDto.name());
            item.setIncomplete(itemDto.incomplete());
            item.setComment(itemDto.comment());
            itemRepository.save(item);
        }

        // Lock the seller only if a new item is actually being added (FR-026: serializes access to
        // nextItemNumber). A pure rename/price change or a category reassignment of existing
        // members never touches that counter, so it never needs this lock — same rationale as
        // ItemService.update(), which never locks the seller for a category-only change.
        boolean hasNewItems = !newIndexes.isEmpty();
        SellerProfile lockedSeller = hasNewItems
                ? sellerRepository.lockById(sellerProfile.getId()).orElseThrow(() -> new SellerNotFoundException(sellerProfile.getId()))
                : sellerProfile;
        int nextItemNumber = hasNewItems ? lockedSeller.getNextItemNumber() : 0;

        // Assign tables for category reassignments and new items in a globally consistent order —
        // ascending category id — same rationale as create()'s lockOrder.
        List<Integer> lockOrder = Stream.concat(categoryChangeIndexes.stream(), newIndexes.stream())
                .sorted(Comparator.comparing(i -> categories.get(i).getId()))
                .toList();
        for (int i : lockOrder) {
            UpdateLotItemDto itemDto = dto.items().get(i);
            EditionCategory category = categories.get(i);
            if (itemDto.id() != null) {
                Item item = currentById.get(itemDto.id());
                item.setName(itemDto.name());
                item.setIncomplete(itemDto.incomplete());
                item.setComment(itemDto.comment());
                int tableNumber = tableAssignmentService.assignTable(sellerProfile, category, edition, item.getId());
                item.setCategory(category);
                item.setTableNumber(tableNumber);
                itemRepository.save(item);
            } else {
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
                item.setTableNumber(tableAssignmentService.assignTable(sellerProfile, category, edition));
                item.setItemNumber(nextItemNumber++);
                itemRepository.save(item);
            }
        }
        if (hasNewItems) {
            lockedSeller.setNextItemNumber(nextItemNumber);
        }

        lot.setName(dto.name());
        lot.setGlobalPrice(dto.globalPrice());
        repository.save(lot);

        return new LotDto(lot.getId(), lot.getName(), lot.getGlobalPrice(),
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
