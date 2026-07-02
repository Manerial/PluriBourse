package org.pluribourse.seller.service;

import com.jPageFlow.utils.FilterDto;
import com.jPageFlow.utils.FilterService;
import lombok.RequiredArgsConstructor;
import org.pluribourse.edition.entity.Edition;
import org.pluribourse.edition.entity.PhaseType;
import org.pluribourse.edition.service.EditionService;
import org.pluribourse.seller.dto.SellerDto;
import org.pluribourse.seller.entity.SellerProfile;
import org.pluribourse.seller.exception.SellerDeletionNotAllowedException;
import org.pluribourse.seller.exception.SellerEmailAlreadyExistsException;
import org.pluribourse.seller.exception.SellerManagementNotAllowedException;
import org.pluribourse.seller.exception.SellerNotFoundException;
import org.pluribourse.seller.mapper.SellerMapper;
import org.pluribourse.seller.repository.SellerRepository;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SellerService {

    private static final int MAX_SEARCH_RESULTS = 50;

    private final SellerRepository repository;
    private final EditionService editionService;
    private final SellerMapper mapper;

    @Transactional(readOnly = true)
    public List<SellerDto> search(String query) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        Edition edition = editionService.getActiveEdition();
        requireDepositPhase(edition);
        List<SellerProfile> sellers = repository.searchByEditionIdAndQuery(edition.getId(), escapeLikeWildcards(query.trim()));
        return mapper.toDtos(sellers.stream().limit(MAX_SEARCH_RESULTS).toList());
    }

    @Transactional
    public SellerDto create(SellerDto dto) {
        Edition edition = editionService.getActiveEdition();
        requireDepositPhase(edition);
        String email = dto.email().trim();
        if (repository.existsByEditionIdAndEmailIgnoreCase(edition.getId(), email)) {
            throw new SellerEmailAlreadyExistsException();
        }
        SellerDto normalized = new SellerDto(dto.id(), dto.firstName().trim(), dto.lastName().trim(), email, dto.phone().trim());
        SellerProfile seller = mapper.toEntity(normalized);
        seller.setEdition(edition);
        return mapper.toDto(repository.save(seller));
    }

    @Transactional(readOnly = true)
    public Page<SellerDto> getSellers(FilterDto filterDto) {
        Edition edition = editionService.getActiveEdition();
        List<SellerProfile> all = repository.findAllByEditionId(edition.getId());
        return FilterService.filterData(all, filterDto, mapper::toDtos);
    }

    @Transactional
    public void delete(Long id) {
        SellerProfile seller = repository.findById(id)
                .orElseThrow(() -> new SellerNotFoundException(id));
        if (seller.getEdition().getPhase() != PhaseType.DEPOSIT) {
            throw new SellerDeletionNotAllowedException();
        }
        repository.delete(seller);
    }

    /**
     * Escapes LIKE wildcard characters so the raw search term is matched literally —
     * without this, "%" or "_" would let a caller enumerate every seller in the edition.
     */
    private static String escapeLikeWildcards(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static void requireDepositPhase(Edition edition) {
        if (edition.getPhase() != PhaseType.DEPOSIT) {
            throw new SellerManagementNotAllowedException();
        }
    }
}
