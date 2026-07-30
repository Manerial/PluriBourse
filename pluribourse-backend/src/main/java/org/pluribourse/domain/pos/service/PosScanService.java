package org.pluribourse.domain.pos.service;

import lombok.RequiredArgsConstructor;
import org.pluribourse.domain.edition.entity.Edition;
import org.pluribourse.domain.edition.service.EditionService;
import org.pluribourse.domain.item.entity.Item;
import org.pluribourse.domain.item.exception.ItemAlreadySoldException;
import org.pluribourse.domain.item.exception.ItemNotFoundException;
import org.pluribourse.domain.item.repository.ItemRepository;
import org.pluribourse.domain.item.service.PhaseGuard;
import org.pluribourse.domain.pos.dto.ScanResultDto;
import org.pluribourse.domain.pos.mapper.ScanResultMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PosScanService {

    private static final Pattern BARCODE_PATTERN = Pattern.compile("^\\d{8}$");

    private final ItemRepository itemRepository;
    private final EditionService editionService;
    private final ScanResultMapper mapper;

    /**
     * Resolves a POS scan (story 4.1). Requires an active edition in the Sale phase
     * ({@link org.pluribourse.domain.item.exception.SalePhaseRequiredException 422 sale-phase-required} otherwise). A barcode that
     * doesn't match the fixed 8-digit {@code SSSSNNNN} format, or that doesn't resolve to an
     * item in the active edition, both surface as the same {@link ItemNotFoundException} (404) —
     * a single "item not found" contract for the volunteer, not two. An item already marked sold
     * throws {@link ItemAlreadySoldException} (409) instead of being returned.
     */
    @Transactional(readOnly = true)
    public ScanResultDto scan(String barcode) {
        Edition edition = editionService.getActiveEdition();
        PhaseGuard.requireSalePhase(edition);

        if (!BARCODE_PATTERN.matcher(barcode).matches()) {
            throw new ItemNotFoundException(barcode);
        }
        int sellerNumber = Integer.parseInt(barcode.substring(0, 4));
        int itemNumber = Integer.parseInt(barcode.substring(4, 8));

        Item item = itemRepository.findByEditionIdAndSellerNumberAndItemNumber(edition.getId(), sellerNumber, itemNumber)
                .orElseThrow(() -> new ItemNotFoundException(barcode));

        if (item.isSold()) {
            throw new ItemAlreadySoldException(item.getId());
        }
        return mapper.toDto(item);
    }
}
