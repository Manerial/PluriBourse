package org.pluribourse.item.service;

import lombok.*;
import org.pluribourse.edition.entity.*;
import org.pluribourse.edition.service.*;
import org.pluribourse.item.dto.*;
import org.pluribourse.item.entity.*;
import org.pluribourse.item.mapper.*;
import org.pluribourse.item.repository.*;
import org.pluribourse.seller.entity.*;
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

            Item item = new Item();
            item.setEdition(edition);
            item.setSellerProfile(sellerProfile);
            item.setCategory(category);
            item.setLot(lot);
            item.setName(itemDto.name());
            item.setIncomplete(itemDto.incomplete());
            item.setComment(itemDto.comment());
            item.setTableNumber(tableAssignmentService.assignTable(sellerProfile, category, edition));
            createdItems[i] = itemRepository.save(item);
        }

        return new LotDto(lot.getId(), lot.getName(), lot.getGlobalPrice(), itemMapper.toDtos(Arrays.asList(createdItems)));
    }
}
