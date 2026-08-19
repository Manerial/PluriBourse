package org.pluribourse.domain.edition.service;

import lombok.*;
import org.pluribourse.domain.edition.dto.*;
import org.pluribourse.domain.edition.entity.*;
import org.pluribourse.domain.edition.exception.*;
import org.pluribourse.domain.edition.mapper.*;
import org.pluribourse.domain.edition.repository.*;
import org.pluribourse.domain.item.repository.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.*;

import java.util.*;

@Service
@RequiredArgsConstructor
public class EditionCategoryService {

    private final EditionCategoryRepository categoryRepository;
    private final EditionRepository editionRepository;
    private final EditionCategoryMapper mapper;
    private final ItemRepository itemRepository;

    @Transactional(readOnly = true)
    public List<EditionCategoryDto> getCategories(Long editionId) {
        requireEditionExists(editionId);
        return categoryRepository.findAllByEditionIdWithTables(editionId)
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    @Transactional
    public List<EditionCategoryDto> saveCategories(Long editionId, List<EditionCategoryDto> dtos) {
        Edition edition = requirePreparationPhase(editionId);
        categoryRepository.deleteAllByEditionId(editionId);
        List<EditionCategory> saved = persistCategories(edition, dtos);
        return saved.stream().map(mapper::toDto).toList();
    }

    @Transactional
    public List<EditionCategoryDto> copyFromEdition(Long targetEditionId, Long sourceEditionId) {
        Edition target = requirePreparationPhase(targetEditionId);
        Edition source = editionRepository.findById(sourceEditionId)
                .orElseThrow(() -> new EditionNotFoundException(sourceEditionId));
        if (source.getPhase() != PhaseType.CLOSED) {
            throw new SourceEditionNotClosedException();
        }
        List<EditionCategoryDto> sourceDtos = categoryRepository.findAllByEditionIdWithTables(sourceEditionId)
                .stream()
                .map(mapper::toDto)
                .toList();
        categoryRepository.deleteAllByEditionId(targetEditionId);
        List<EditionCategory> saved = persistCategories(target, sourceDtos);
        return saved.stream().map(mapper::toDto).toList();
    }

    private List<EditionCategory> persistCategories(Edition edition, List<EditionCategoryDto> dtos) {
        List<EditionCategory> categories = new ArrayList<>();
        for (int i = 0; i < dtos.size(); i++) {
            EditionCategory entity = mapper.toEntity(dtos.get(i), i);
            entity.setEdition(edition);
            categories.add(entity);
        }
        return categoryRepository.saveAll(categories);
    }

    /**
     * A Deposit→Preparation rollback preserves items (AC 10 of Story 3.2), so an edition can be
     * back in PREPARATION while items still reference its categories — deleteAllByEditionId()
     * would then hit the FK constraint on items.category_id (no cascade by design).
     */
    private Edition requirePreparationPhase(Long editionId) {
        Edition edition = requireEditionExists(editionId);
        if (edition.getPhase() != PhaseType.PREPARATION) {
            throw new CategoriesLockedException();
        }
        if (itemRepository.existsByEditionId(editionId)) {
            throw new CategoriesInUseException();
        }
        return edition;
    }

    private Edition requireEditionExists(Long editionId) {
        return editionRepository.findById(editionId)
                .orElseThrow(() -> new EditionNotFoundException(editionId));
    }

}
