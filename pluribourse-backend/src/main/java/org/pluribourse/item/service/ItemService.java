package org.pluribourse.item.service;

import lombok.*;
import org.pluribourse.edition.entity.*;
import org.pluribourse.edition.service.*;
import org.pluribourse.item.dto.*;
import org.pluribourse.item.entity.*;
import org.pluribourse.item.exception.*;
import org.pluribourse.item.mapper.*;
import org.pluribourse.item.repository.*;
import org.pluribourse.seller.entity.*;
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
        item.setTableNumber(tableAssignmentService.assignTable(sellerProfile, category, edition));
        return mapper.toDto(repository.save(item));
    }

    @Transactional
    public ItemDto update(Long id, CreateItemDto dto) {
        Item item = findById(id);
        PhaseGuard.requireDepositPhase(item.getEdition());
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
        repository.delete(item);
    }

    private Item findById(Long id) {
        return repository.findById(id).orElseThrow(() -> new ItemNotFoundException(id));
    }
}
