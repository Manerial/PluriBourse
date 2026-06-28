package org.pluribourse.edition.service;

import lombok.*;
import org.pluribourse.edition.dto.EditionDto;
import org.pluribourse.edition.entity.*;
import org.pluribourse.edition.mapper.*;
import org.pluribourse.edition.repository.*;
import org.pluribourse.shared.exception.*;
import org.pluribourse.shared.instanceconfig.service.*;
import org.springframework.http.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.*;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class EditionService {

    private static final List<PhaseType> ACTIVE_PHASES = List.of(
            PhaseType.PREPARATION, PhaseType.DEPOSIT, PhaseType.SALE, PhaseType.POST_SALE
    );

    private final EditionRepository repository;
    private final EditionMapper mapper;
    private final GlobalInstanceConfigService instanceConfigService;

    @Transactional(readOnly = true)
    public List<EditionDto> getAllEditions() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .map(mapper::toDto)
                .toList();
    }

    @Transactional
    public EditionDto createEdition(EditionDto dto) {
        if (repository.existsByPhaseIn(ACTIVE_PHASES)) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "edition-already-active",
                    "An edition is already active. Close the current edition before creating a new one.");
        }
        Edition edition = new Edition();
        edition.setName(dto.name());
        edition.setPhase(PhaseType.PREPARATION);
        edition.setCommissionRate(dto.commissionRate() != null ? dto.commissionRate() : instanceConfigService.getDefaultCommissionRate());
        edition.setDocumentLanguage(dto.documentLanguage() != null ? dto.documentLanguage() : instanceConfigService.getDefaultDocumentLanguage());
        edition.setCreatedAt(LocalDate.now());
        return mapper.toDto(repository.save(edition));
    }

    @Transactional(readOnly = true)
    public EditionDto getEditionById(Long id) {
        return mapper.toDto(findById(id));
    }

    @Transactional
    public EditionDto updateEdition(Long id, EditionDto dto) {
        Edition edition = findById(id);
        edition.setName(dto.name());
        if (edition.getPhase() != PhaseType.PREPARATION
                && dto.commissionRate() != null
                && dto.commissionRate().compareTo(edition.getCommissionRate()) != 0) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "commission-rate-frozen",
                    "Commission rate is locked once the Deposit phase has started.");
        }
        if (dto.commissionRate() != null) {
            edition.setCommissionRate(dto.commissionRate());
        }
        if (dto.documentLanguage() != null) {
            edition.setDocumentLanguage(dto.documentLanguage());
        }
        return mapper.toDto(repository.save(edition));
    }

    @Transactional
    public void deleteEdition(Long id) {
        Edition edition = findById(id);
        if (edition.getPhase() != PhaseType.PREPARATION) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "edition-cannot-be-deleted",
                    "Editions that have progressed past Preparation phase cannot be deleted.");
        }
        repository.delete(edition);
    }

    private Edition findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "edition-not-found",
                        "Edition not found: " + id));
    }
}
