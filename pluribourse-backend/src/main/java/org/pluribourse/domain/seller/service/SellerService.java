package org.pluribourse.domain.seller.service;

import com.jPageFlow.utils.*;
import lombok.*;
import org.pluribourse.domain.edition.entity.*;
import org.pluribourse.domain.edition.exception.*;
import org.pluribourse.domain.edition.repository.*;
import org.pluribourse.domain.edition.service.*;
import org.pluribourse.domain.item.entity.*;
import org.pluribourse.domain.item.repository.*;
import org.pluribourse.domain.payout.repository.SettlementRepository;
import org.pluribourse.domain.seller.dto.*;
import org.pluribourse.domain.seller.entity.*;
import org.pluribourse.domain.seller.exception.*;
import org.pluribourse.domain.seller.mapper.*;
import org.pluribourse.domain.seller.repository.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.*;
import org.springframework.util.*;

import java.util.*;

@Service
@RequiredArgsConstructor
public class SellerService {

    private static final int MAX_SEARCH_RESULTS = 50;

    private final SellerRepository repository;
    private final EditionService editionService;
    private final EditionRepository editionRepository;
    private final SellerMapper mapper;
    private final ItemRepository itemRepository;
    private final SettlementRepository settlementRepository;

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
        // Lock the edition first (FR-026): serializes concurrent seller creations so the second
        // caller reads the counter after the first has already incremented it, instead of both
        // reading the same value. A persisted counter (not MAX(sellerNumber)+1) is required because
        // a deleted seller (FR-021) must never free its number for reuse by the next one created.
        Edition lockedEdition = editionRepository.lockById(edition.getId())
                .orElseThrow(() -> new EditionNotFoundException(edition.getId()));
        int sellerNumber = lockedEdition.getNextSellerNumber();
        if (sellerNumber > Item.MAX_BARCODE_SEGMENT) {
            throw new TooManySellersException(edition.getId());
        }
        seller.setSellerNumber(sellerNumber);
        lockedEdition.setNextSellerNumber(sellerNumber + 1);
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
        boolean hasNoRegisteredArticles = !itemRepository.existsBySellerProfileId(id);
        if (!seller.canBeDeleted(hasNoRegisteredArticles)) {
            throw new SellerDeletionNotAllowedException();
        }
        // A settled/unclaimed payout (story 5.1) is a financial record — deleting its seller must
        // never silently cascade-delete it (the settlements FK has no deleteCascade specifically
        // for this reason). Checked here rather than left to surface as a raw constraint violation.
        if (settlementRepository.findBySellerProfileId(id).isPresent()) {
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
