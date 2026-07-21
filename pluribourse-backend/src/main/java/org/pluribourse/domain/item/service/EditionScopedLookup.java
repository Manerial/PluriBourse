package org.pluribourse.domain.item.service;

import lombok.*;
import org.pluribourse.domain.edition.entity.*;
import org.pluribourse.domain.edition.repository.*;
import org.pluribourse.domain.item.exception.*;
import org.pluribourse.domain.seller.entity.*;
import org.pluribourse.domain.seller.exception.*;
import org.pluribourse.domain.seller.repository.*;
import org.springframework.stereotype.*;

/**
 * Sellers and categories are resolved by id from client input but must also belong to the
 * edition the item/lot is being attached to — otherwise a stale id from a past edition would be
 * silently accepted and desync TableAssignmentService's per-edition table-load counting.
 */
@Component
@RequiredArgsConstructor
public class EditionScopedLookup {

    private final SellerRepository sellerRepository;
    private final EditionCategoryRepository categoryRepository;

    public SellerProfile findSellerInEdition(Long sellerProfileId, Edition edition) {
        SellerProfile sellerProfile = sellerRepository.findById(sellerProfileId)
                .orElseThrow(() -> new SellerNotFoundException(sellerProfileId));
        if (!sellerProfile.getEdition().getId().equals(edition.getId())) {
            throw new SellerNotFoundException(sellerProfileId);
        }
        return sellerProfile;
    }

    public EditionCategory findCategoryInEdition(Long categoryId, Edition edition) {
        EditionCategory category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
        if (!category.getEdition().getId().equals(edition.getId())) {
            throw new CategoryNotFoundException(categoryId);
        }
        return category;
    }
}
