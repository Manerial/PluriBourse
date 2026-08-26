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

@Service
@RequiredArgsConstructor
public class ItemService {

    private final ItemRepository repository;
    private final EditionScopedLookup editionScopedLookup;
    private final EditionService editionService;
    private final TableAssignmentService tableAssignmentService;
    private final SellerRepository sellerRepository;
    private final ItemMapper mapper;

    @Transactional(readOnly = true)
    public List<ItemDto> getBySellerProfile(Long sellerProfileId) {
        Edition edition = editionService.getActiveEdition();
        SellerProfile sellerProfile = editionScopedLookup.findSellerInEdition(sellerProfileId, edition);
        return mapper.toDtos(repository.findAllBySellerProfileIdOrderByNameAsc(sellerProfile.getId()));
    }

    @Transactional
    public ItemDto create(CreateItemDto dto) {
        Edition edition = editionService.getActiveEdition();
        PhaseGuard.requireDepositPhase(edition);
        SellerProfile sellerProfile = editionScopedLookup.findSellerInEdition(dto.sellerProfileId(), edition);
        EditionCategory category = editionScopedLookup.findCategoryInEdition(dto.categoryId(), edition);

        Item item = mapper.toEntity(dto);
        item.setEdition(edition);
        item.setSellerProfile(sellerProfile);
        item.setCategory(category);
        // Lock the seller first (FR-026): serializes concurrent item creations for this seller so
        // the second caller reads nextItemNumber after the first has already incremented it. A
        // persisted counter (not MAX(itemNumber)+1) is required because a deleted item (FR-024)
        // must never free its number for reuse by the next one created. Locking before
        // assignTable (which takes a per-category lock) keeps acquisition order consistent with
        // LotService.create() — both lock the seller before any category — avoiding an ABBA
        // deadlock between concurrent single-item and lot creations.
        SellerProfile lockedSeller = sellerRepository.lockById(sellerProfile.getId())
                .orElseThrow(() -> new SellerNotFoundException(sellerProfile.getId()));
        int itemNumber = lockedSeller.getNextItemNumber();
        if (itemNumber > Item.MAX_BARCODE_SEGMENT) {
            throw new TooManyItemsException(sellerProfile.getId());
        }
        item.setTableNumber(tableAssignmentService.assignTable(sellerProfile, category, edition));
        item.setItemNumber(itemNumber);
        lockedSeller.setNextItemNumber(itemNumber + 1);
        return mapper.toDto(repository.save(item));
    }

    @Transactional
    public ItemDto update(Long id, CreateItemDto dto) {
        Item item = findById(id);
        PhaseGuard.requireDepositPhase(item.getEdition());
        if (item.getLot() != null) {
            throw new ItemBelongsToLotException(id);
        }
        mapper.updateEntityFromDto(dto, item);
        if (!item.getCategory().getId().equals(dto.categoryId())) {
            EditionCategory category = editionScopedLookup.findCategoryInEdition(dto.categoryId(), item.getEdition());
            int tableNumber = tableAssignmentService.assignTable(item.getSellerProfile(), category, item.getEdition(), item.getId());
            item.setCategory(category);
            item.setTableNumber(tableNumber);
        }
        return mapper.toDto(repository.save(item));
    }

    @Transactional
    public ItemDto updateCompleteness(Long id, ItemCompletenessDto dto) {
        Item item = findById(id);
        item.setIncomplete(dto.incomplete());
        item.setComment(dto.comment());
        return mapper.toDto(repository.save(item));
    }

    @Transactional
    public void delete(Long id) {
        Item item = findById(id);
        PhaseGuard.requireDepositPhase(item.getEdition());
        Lot lot = item.getLot();
        if (lot != null && lot.getItems().size() <= 2) {
            throw new LotBelowMinimumMembersException(lot.getId());
        }
        repository.delete(item);
    }

    private Item findById(Long id) {
        return repository.findById(id).orElseThrow(() -> new ItemNotFoundException(id));
    }
}
