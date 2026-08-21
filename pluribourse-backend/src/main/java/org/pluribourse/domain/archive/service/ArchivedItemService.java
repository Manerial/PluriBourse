package org.pluribourse.domain.archive.service;

import com.jPageFlow.utils.*;
import lombok.*;
import org.pluribourse.domain.archive.dto.*;
import org.pluribourse.domain.archive.entity.*;
import org.pluribourse.domain.archive.mapper.*;
import org.pluribourse.domain.archive.repository.*;
import org.pluribourse.domain.edition.service.*;
import org.pluribourse.domain.item.exception.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.*;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ArchivedItemService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("name", "categoryName", "sold");

    private final EditionService editionService;
    private final ArchivedItemRepository archivedItemRepository;
    private final ArchivedItemMapper mapper;

    /**
     * {@code editionService.requireEdition} is used purely to 404 on an unknown edition id.
     * {@code archived_items} rows only ever exist for editions {@code EditionArchivingService} has
     * actually archived, so a non-archived (but existing) edition id naturally yields an empty page
     * with no data-integrity risk.
     */
    @Transactional(readOnly = true)
    public ArchivedItemPageDto getArchivedCatalog(Long editionId, ArchivedItemFilterDto filter) {
        editionService.requireEdition(editionId);
        validateSort(filter.sort());
        List<ArchivedItem> all = archivedItemRepository.findAllByEditionId(editionId);
        List<ArchivedItem> filtered = all.stream()
                .filter(i -> matches(filter.name(), i.getName()))
                .filter(i -> filter.categoryName() == null || filter.categoryName().isBlank() || filter.categoryName().equals(i.getCategoryName()))
                .filter(i -> filter.sold() == null || filter.sold() == i.isSold())
                .toList();

        FilterDto pagingOnly = new FilterDto();
        pagingOnly.setPage(clampPage(filter.page(), filter.size(), filtered.size()));
        pagingOnly.setSize(filter.size());
        pagingOnly.setSort(filter.sort());
        Page<ArchivedItemDto> page = FilterService.filterData(filtered, pagingOnly, mapper::toDtos);
        return new ArchivedItemPageDto(page);
    }

    /**
     * Same {@code Page.empty()}-on-overflow workaround as {@code ItemCatalogService.clampPage}.
     */
    private static int clampPage(int requestedPage, int size, int totalFilteredCount) {
        int totalPages = (int) Math.ceil((double) totalFilteredCount / size);
        return totalPages == 0 ? 0 : Math.min(requestedPage, totalPages - 1);
    }

    private static void validateSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return;
        }
        String field = sort.split(",", 2)[0];
        if (!ALLOWED_SORT_FIELDS.contains(field)) {
            throw new InvalidSortFieldException(field);
        }
    }

    private static boolean matches(String query, String value) {
        if (query == null || query.isBlank()) {
            return true;
        }
        return value != null && value.toLowerCase().contains(query.toLowerCase());
    }
}
