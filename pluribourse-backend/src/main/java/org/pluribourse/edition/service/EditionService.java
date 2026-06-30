package org.pluribourse.edition.service;

import lombok.*;
import org.pluribourse.edition.dto.*;
import org.pluribourse.edition.entity.*;
import org.pluribourse.edition.exception.*;
import org.pluribourse.edition.mapper.*;
import org.pluribourse.edition.repository.*;
import org.pluribourse.instanceconfig.service.*;
import org.pluribourse.shared.sse.*;
import org.pluribourse.user.enums.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;

import java.math.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class EditionService {

    private final EditionRepository repository;
    private final EditionMapper mapper;
    private final GlobalInstanceConfigService instanceConfigService;
    private final SseEmitterRegistry sseEmitterRegistry;

    private Edition findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EditionNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<EditionDto> getAllEditions() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .map(mapper::toDto)
                .toList();
    }

    @Transactional
    public EditionDto createEdition(EditionDto dto) {
        if (repository.existsByPhaseIn(PhaseType.ACTIVE)) {
            throw new EditionAlreadyActiveException();
        }
        BigDecimal commissionRate = dto.commissionRate() != null ? dto.commissionRate() : instanceConfigService.getDefaultCommissionRate();
        Language documentLanguage = dto.documentLanguage() != null ? dto.documentLanguage() : instanceConfigService.getDefaultDocumentLanguage();
        return mapper.toDto(repository.save(mapper.toEntity(dto, commissionRate, documentLanguage)));
    }

    @Transactional(readOnly = true)
    public EditionDto getEditionById(Long id) {
        return mapper.toDto(findById(id));
    }

    @Transactional
    public EditionDto updateEdition(Long id, EditionDto dto) {
        Edition edition = findById(id);
        if (edition.getPhase() != PhaseType.PREPARATION && dto.commissionRate() != null) {
            throw new CommissionRateFrozenException();
        }
        mapper.updateEditionFromDto(dto, edition);
        return mapper.toDto(repository.save(edition));
    }

    @Transactional
    public void deleteEdition(Long id) {
        Edition edition = findById(id);
        if (edition.getPhase() != PhaseType.PREPARATION) {
            throw new EditionCannotBeDeletedException();
        }
        repository.delete(edition);
    }

    @Transactional
    public EditionDto advancePhase(Long id) {
        Edition edition = findById(id);
        PhaseType previousPhase = edition.getPhase();
        PhaseType newPhase = computeNextPhase(previousPhase);
        return savePhaseThenSendEvent(id, edition, newPhase, previousPhase);
    }

    @Transactional
    public EditionDto rollbackPhase(Long id) {
        Edition edition = findById(id);
        PhaseType previousPhase = edition.getPhase();
        PhaseType newPhase = computePreviousPhase(previousPhase, edition.isArchived());
        return savePhaseThenSendEvent(id, edition, newPhase, previousPhase);
    }

    private PhaseType computeNextPhase(PhaseType current) {
        return switch (current) {
            case PREPARATION -> PhaseType.DEPOSIT;
            case DEPOSIT -> PhaseType.SALE;
            case SALE -> PhaseType.POST_SALE;
            case POST_SALE -> PhaseType.CLOSED;
            case CLOSED -> throw new PhaseAlreadyClosedException();
        };
    }

    private PhaseType computePreviousPhase(PhaseType current, boolean archived) {
        return switch (current) {
            case PREPARATION -> throw new PhaseRollbackFromPreparationException();
            case DEPOSIT -> PhaseType.PREPARATION;
            case SALE -> PhaseType.DEPOSIT;
            case POST_SALE -> PhaseType.SALE;
            case CLOSED -> {
                if (archived) {
                    throw new PhaseRollbackAfterArchiveException();
                }
                yield PhaseType.POST_SALE;
            }
        };
    }

    private EditionDto savePhaseThenSendEvent(Long id, Edition edition, PhaseType newPhase, PhaseType previousPhase) {
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
