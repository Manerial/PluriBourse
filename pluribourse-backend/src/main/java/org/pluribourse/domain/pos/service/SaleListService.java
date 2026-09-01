package org.pluribourse.domain.pos.service;

import com.jPageFlow.utils.FilterDto;
import com.jPageFlow.utils.FilterService;
import lombok.RequiredArgsConstructor;
import org.pluribourse.domain.edition.entity.Edition;
import org.pluribourse.domain.edition.service.EditionService;
import org.pluribourse.domain.pos.dto.SaleListFilterDto;
import org.pluribourse.domain.pos.dto.SaleListItemDto;
import org.pluribourse.domain.pos.dto.SaleListPageDto;
import org.pluribourse.domain.pos.entity.Sale;
import org.pluribourse.domain.pos.exception.InvalidSortFieldException;
import org.pluribourse.domain.pos.mapper.SaleListMapper;
import org.pluribourse.domain.pos.repository.SaleRepository;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Sales list screen (story 4.7, FR-108). Structural copy of {@code ItemCatalogService}: the whole
 * edition's sales are loaded in one {@code JOIN FETCH}, filtered in memory (date range, cashier),
 * then sort + pagination are delegated to {@code FilterService.filterData()} (JPageFlow 1.7.0).
 */
@Service
@RequiredArgsConstructor
public class SaleListService {

    /**
     * Entity property paths reachable via the {@code sort} query param — must match exactly the
     * {@code mat-sort-header} ids used by the frontend. {@code FilterService.compare()} resolves
     * these by reflection on {@code Sale}; an unrecognized field crashes deep inside that
     * reflection with a raw {@code NullPointerException}, so every accepted value is whitelisted.
     */
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "soldAt", "user.username", "paymentMethod", "total"
    );

    private final EditionService editionService;
    private final SaleRepository saleRepository;
    private final SaleListMapper mapper;

    @Transactional(readOnly = true)
    public SaleListPageDto getSales(SaleListFilterDto filter) {
        validateSort(filter.sort());
        Edition edition = editionService.getActiveEdition();
        String currency = edition.getCurrency();
        List<Sale> all = saleRepository.findAllByEditionIdForList(edition.getId());
        List<Sale> filtered = all.stream()
                // soldAt >= dateFrom AND soldAt <= dateTo — both bounds inclusive (user decision,
                // story 4.7 Q3). A missing bound = no limit on that side.
                .filter(s -> filter.dateFrom() == null || !s.getSoldAt().isBefore(filter.dateFrom()))
                .filter(s -> filter.dateTo() == null || !s.getSoldAt().isAfter(filter.dateTo()))
                .filter(s -> filter.cashier() == null || filter.cashier().isBlank()
                        || filter.cashier().equalsIgnoreCase(s.getUser().getUsername()))
                .toList();

        FilterDto pagingOnly = new FilterDto();
        pagingOnly.setPage(clampPage(filter.page(), filter.size(), filtered.size()));
        pagingOnly.setSize(filter.size());
        pagingOnly.setSort(filter.sort());
        Page<SaleListItemDto> page = FilterService.filterData(filtered, pagingOnly,
                sales -> sales.stream().map(s -> mapper.toDto(s, currency)).toList());
        return new SaleListPageDto(page);
    }

    @Transactional(readOnly = true)
    public List<String> getCashiers() {
        return saleRepository.findDistinctCashierUsernamesByEditionId(
                editionService.getActiveEdition().getId());
    }

    /**
     * JPageFlow's {@code FilterService} responds to a page number past the last available page
     * with {@code Page.empty()} — {@code totalElements} included — silently discarding the real
     * count instead of just returning an empty content list for that page. Clamping the requested
     * page to the last valid one keeps the reported total accurate. Copied verbatim from
     * {@code ItemCatalogService} (see story 6.1); not factored into a shared helper — CLAUDE.md
     * discourages premature abstraction and coupling a {@code pos} service to an {@code item} one.
     */
    private static int clampPage(int requestedPage, int size, int totalFilteredCount) {
        int totalPages = (int) Math.ceil((double) totalFilteredCount / size);
        return totalPages == 0 ? 0 : Math.min(requestedPage, totalPages - 1);
    }

    /**
     * {@code FilterService.compare()} resolves {@code sort} by reflection on {@code Sale} with no
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
}
