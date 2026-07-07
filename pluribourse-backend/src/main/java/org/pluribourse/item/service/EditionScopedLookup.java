package org.pluribourse.item.service;

import lombok.*;
import org.pluribourse.edition.entity.*;
import org.pluribourse.edition.repository.*;
import org.pluribourse.item.exception.*;
import org.pluribourse.seller.entity.*;
import org.pluribourse.seller.exception.*;
import org.pluribourse.seller.repository.*;
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
