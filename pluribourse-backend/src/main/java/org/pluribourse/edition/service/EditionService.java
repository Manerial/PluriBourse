package org.pluribourse.edition.service;

import lombok.*;
import org.pluribourse.edition.dto.*;
import org.pluribourse.edition.entity.*;
import org.pluribourse.edition.mapper.*;
import org.pluribourse.edition.repository.*;
import org.pluribourse.shared.exception.*;
import org.pluribourse.shared.instanceconfig.service.*;
import org.pluribourse.shared.sse.*;
import org.pluribourse.user.enums.*;
import org.springframework.http.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;

import java.math.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class EditionService {

    private static final List<PhaseType> ACTIVE_PHASES = List.of(
            PhaseType.PREPARATION,
            PhaseType.DEPOSIT,
            PhaseType.SALE,
            PhaseType.POST_SALE
    );

    private final EditionRepository repository;
    private final EditionMapper mapper;
    private final GlobalInstanceConfigService instanceConfigService;
    private final SseEmitterRegistry sseEmitterRegistry;

    private Edition findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "edition-not-found", "Edition not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<EditionDto> getAllEditions() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .map(mapper::toDto)
                .toList();
    }

    @Transactional
    public EditionDto createEdition(EditionDto dto) {
        if (repository.existsByPhaseIn(ACTIVE_PHASES)) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_CONTENT, "edition-already-active", "An edition is already active. Close the current edition before creating a new one.");
        }
        BigDecimal commissionRate = dto.commissionRate() != null ? dto.commissionRate() : instanceConfigService.getDefaultCommissionRate();
        Language documentLanguage = dto.documentLanguage() != null ? dto.documentLanguage() : instanceConfigService.getDefaultDocumentLanguage();
        Edition entity = mapper.toEntity(dto, commissionRate, documentLanguage);
        return mapper.toDto(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public EditionDto getEditionById(Long id) {
        return mapper.toDto(findById(id));
    }

    @Transactional
    public EditionDto updateEdition(Long id, EditionDto dto) {
        Edition edition = findById(id);
        if (edition.getPhase() != PhaseType.PREPARATION && dto.commissionRate() != null) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_CONTENT, "commission-rate-frozen", "Commission rate is locked once the Deposit phase has started.");
        }
        mapper.updateEditionFromDto(dto, edition);
        return mapper.toDto(repository.save(edition));
    }

    @Transactional
    public EditionDto updateCommissionRate(Long id, BigDecimal newRate) {
        Edition edition = findById(id);
        if (edition.getPhase() != PhaseType.PREPARATION) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_CONTENT, "commission-rate-frozen", "Commission rate is locked once the Deposit phase has started.");
        }
        edition.setCommissionRate(newRate);
        return mapper.toDto(repository.save(edition));
    }

    @Transactional
    public void deleteEdition(Long id) {
        Edition edition = findById(id);
        if (edition.getPhase() != PhaseType.PREPARATION) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_CONTENT, "edition-cannot-be-deleted", "Editions that have progressed past Preparation phase cannot be deleted.");
        }
        repository.delete(edition);
    }

    @Transactional
    public EditionDto advancePhase(Long id) {
        Edition edition = findById(id);
        PhaseType previousPhase = edition.getPhase();
        PhaseType newPhase = computeNextPhase(previousPhase);
        return changePhase(id, edition, newPhase, previousPhase);
    }

    @Transactional
    public EditionDto rollbackPhase(Long id) {
        Edition edition = findById(id);
        PhaseType previousPhase = edition.getPhase();
        PhaseType newPhase = computePreviousPhase(previousPhase, edition.isArchived());
        return changePhase(id, edition, newPhase, previousPhase);
    }

    private PhaseType computeNextPhase(PhaseType current) {
        return switch (current) {
            case PREPARATION -> PhaseType.DEPOSIT;
            case DEPOSIT -> PhaseType.SALE;
            case SALE -> PhaseType.POST_SALE;
            case POST_SALE -> PhaseType.CLOSED;
            case CLOSED -> throw new BusinessException(HttpStatus.UNPROCESSABLE_CONTENT, "phase-already-closed", "Edition is already closed. Cannot advance further.");
        };
    }

    private PhaseType computePreviousPhase(PhaseType current, boolean archived) {
        return switch (current) {
            case PREPARATION -> throw new BusinessException(HttpStatus.UNPROCESSABLE_CONTENT, "phase-cannot-rollback-from-preparation", "Cannot roll back from Preparation phase.");
            case DEPOSIT -> PhaseType.PREPARATION;
            case SALE -> PhaseType.DEPOSIT;
            case POST_SALE -> PhaseType.SALE;
            case CLOSED -> {
                if (archived) {
                    throw new BusinessException(HttpStatus.UNPROCESSABLE_CONTENT, "phase-rollback-disabled-after-archive", "Cannot roll back from Closed phase after the edition has been archived.");
                }
                yield PhaseType.POST_SALE;
            }
        };
    }

    private EditionDto changePhase(Long id, Edition edition, PhaseType newPhase, PhaseType previousPhase) {
        edition.setPhase(newPhase);
        Edition saved = repository.save(edition);
        PhaseChangedEventDto event = new PhaseChangedEventDto(id, newPhase, previousPhase);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sseEmitterRegistry.broadcast("phase-changed", event);
            }
        });
        return mapper.toDto(saved);
    }
}
