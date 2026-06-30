package org.pluribourse.edition.service;

import lombok.*;
import org.pluribourse.edition.dto.*;
import org.pluribourse.edition.entity.*;
import org.pluribourse.edition.mapper.*;
import org.pluribourse.edition.repository.*;
import org.pluribourse.shared.exception.*;
import org.springframework.http.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.*;

import java.util.*;

@Service
@RequiredArgsConstructor
public class EditionCategoryService {

    private final EditionCategoryRepository categoryRepository;
    private final EditionRepository editionRepository;
    private final EditionCategoryMapper mapper;

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
        validateCategories(dtos);
        categoryRepository.deleteAllByEditionId(editionId);
        List<EditionCategory> saved = persistCategories(edition, dtos);
        return saved.stream().map(mapper::toDto).toList();
    }

    @Transactional
    public List<EditionCategoryDto> copyFromEdition(Long targetEditionId, Long sourceEditionId) {
        Edition target = requirePreparationPhase(targetEditionId);
        Edition source = editionRepository.findById(sourceEditionId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "edition-not-found", "Source edition not found: " + sourceEditionId));
        if (source.getPhase() != PhaseType.CLOSED) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_CONTENT, "source-edition-not-closed", "Can only copy categories from a CLOSED edition.");
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
            categories.add(categoryRepository.save(entity));
        }
        return categories;
    }

    private Edition requirePreparationPhase(Long editionId) {
        Edition edition = requireEditionExists(editionId);
        if (edition.getPhase() != PhaseType.PREPARATION) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_CONTENT, "categories-locked", "Categories and table assignments are locked once the Deposit phase has started.");
        }
        return edition;
    }

    private Edition requireEditionExists(Long editionId) {
        return editionRepository.findById(editionId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "edition-not-found", "Edition not found: " + editionId));
    }

    private void validateCategories(List<EditionCategoryDto> dtos) {
        for (EditionCategoryDto dto : dtos) {
            if (dto.name() == null || dto.name().isBlank()) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_CONTENT, "category-name-required", "Category name must not be blank.");
            }
            if (dto.tableNumbers() == null || dto.tableNumbers().isEmpty()) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_CONTENT, "category-missing-table", "Category '" + dto.name() + "' must have at least one table assigned.");
            }
        }
    }
}
