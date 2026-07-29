package org.pluribourse.domain.item.service;

import com.jPageFlow.utils.*;
import lombok.*;
import org.pluribourse.domain.edition.entity.*;
import org.pluribourse.domain.edition.exception.*;
import org.pluribourse.domain.edition.service.*;
import org.pluribourse.domain.item.dto.*;
import org.pluribourse.domain.item.entity.*;
import org.pluribourse.domain.item.exception.*;
import org.pluribourse.domain.item.mapper.*;
import org.pluribourse.domain.item.repository.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.*;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ItemCatalogService {

    /**
     * Entity property paths reachable via the {@code sort} query param — must match exactly the
     * {@code mat-sort-header} ids used by the frontend. {@code FilterService.compare()} resolves
     * these by reflection on {@code Item}; an unrecognized field crashes deep inside that
     * reflection with a raw {@code NullPointerException}, so every accepted value is whitelisted
     * here rather than passed through unchecked.
     */
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "name", "category.name", "tableNumber", "sellerProfile.lastName", "price", "incomplete", "sold"
    );

    private final EditionService editionService;
    private final ItemRepository itemRepository;
    private final ItemMapper mapper;

    /**
     * Scoped to the currently active edition only — a closed or archived edition's catalog is out
     * of scope for this story (see a future story for historical/past-edition catalog browsing).
     * Filtering happens on the fully loaded item list rather than via JPageFlow's reflection-based
     * {@code filterParams}: two filter dimensions here (barcode, exact-match table number) are
     * unsafe or impossible for that mechanism (see Dev Notes on Story 6.1).
     */
    @Transactional(readOnly = true)
    public ItemCatalogPageDto getCatalog(ItemCatalogFilterDto filter) {
        validateSort(filter.sort());
        Edition edition = editionService.getActiveEdition();
        List<Item> all = itemRepository.findAllByEditionIdForCatalog(edition.getId());
        List<Item> filtered = all.stream()
                .filter(i -> matches(filter.name(), i.getName()))
                .filter(i -> matchesBarcode(filter.barcode(), i))
                .filter(i -> filter.categoryId() == null || filter.categoryId().equals(i.getCategory().getId()))
                .filter(i -> filter.tableNumber() == null || filter.tableNumber().equals(i.getTableNumber()))
                .filter(i -> filter.sold() == null || filter.sold() == i.isSold())
                .filter(i -> filter.incomplete() == null || filter.incomplete() == i.isIncomplete())
                .filter(i -> matches(filter.sellerName(), i.getSellerProfile().getFirstName() + " " + i.getSellerProfile().getLastName()))
                .toList();

        FilterDto pagingOnly = new FilterDto();
        pagingOnly.setPage(clampPage(filter.page(), filter.size(), filtered.size()));
        pagingOnly.setSize(filter.size());
        pagingOnly.setSort(filter.sort());
        Page<ItemCatalogDto> page = FilterService.filterData(filtered, pagingOnly, mapper::toCatalogDtos);
        return new ItemCatalogPageDto(page);
    }

    /**
     * JPageFlow's {@code FilterService} responds to a page number past the last available page
     * with {@code Page.empty()} — {@code totalElements} included — silently discarding the real
     * count instead of just returning an empty content list for that page. Clamping the requested
     * page to the last valid one keeps the reported total accurate.
     */
    private static int clampPage(int requestedPage, int size, int totalFilteredCount) {
        int totalPages = (int) Math.ceil((double) totalFilteredCount / size);
        return totalPages == 0 ? 0 : Math.min(requestedPage, totalPages - 1);
    }

    /**
     * {@code FilterService.compare()} resolves {@code sort} by reflection on {@code Item} with no
     * validation of its own — an unrecognized field crashes with a raw {@code NullPointerException}
     * instead of a clean error (confirmed by tracing {@code FilterService.getField()}'s recursive
     * superclass walk, which dereferences a {@code null} class once it exhausts {@code Object}).
     */
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

    private static boolean matchesBarcode(String query, Item item) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String digitsOnly = item.getFormattedBarcode().replace("-", "");
        String queryDigitsOnly = query.replaceAll("[^0-9]", "");
        if (queryDigitsOnly.isEmpty()) {
            return false;
        }
        return digitsOnly.contains(queryDigitsOnly);
    }
}
