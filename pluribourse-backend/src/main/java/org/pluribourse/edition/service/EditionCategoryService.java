package org.pluribourse.edition.service;

import lombok.RequiredArgsConstructor;
import org.pluribourse.edition.dto.EditionCategoryDto;
import org.pluribourse.edition.entity.Edition;
import org.pluribourse.edition.entity.EditionCategory;
import org.pluribourse.edition.entity.PhaseType;
import org.pluribourse.edition.mapper.EditionCategoryMapper;
import org.pluribourse.edition.repository.EditionCategoryRepository;
import org.pluribourse.edition.repository.EditionRepository;
import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

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
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "edition-not-found",
                        "Source edition not found: " + sourceEditionId));
        if (source.getPhase() != PhaseType.CLOSED) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "source-edition-not-closed",
                    "Can only copy categories from a CLOSED edition.");
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
            EditionCategoryDto dto = dtos.get(i);
            EditionCategory category = new EditionCategory();
            category.setEdition(edition);
            category.setName(dto.name());
            category.setDisplayOrder(i);
            category.setTableNumbers(new HashSet<>(dto.tableNumbers()));
            categories.add(categoryRepository.save(category));
        }
        return categories;
    }

    private Edition requirePreparationPhase(Long editionId) {
        Edition edition = requireEditionExists(editionId);
        if (edition.getPhase() != PhaseType.PREPARATION) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "categories-locked",
                    "Categories and table assignments are locked once the Deposit phase has started.");
        }
        return edition;
    }

    private Edition requireEditionExists(Long editionId) {
        return editionRepository.findById(editionId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "edition-not-found",
                        "Edition not found: " + editionId));
    }

    private void validateCategories(List<EditionCategoryDto> dtos) {
        for (EditionCategoryDto dto : dtos) {
            if (dto.name() == null || dto.name().isBlank()) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "category-name-required",
                        "Category name must not be blank.");
            }
            if (dto.tableNumbers() == null || dto.tableNumbers().isEmpty()) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "category-missing-table",
                        "Category '" + dto.name() + "' must have at least one table assigned.");
            }
        }
    }
}
