package org.pluribourse.domain.edition.service;

import lombok.*;
import org.pluribourse.domain.edition.dto.*;
import org.pluribourse.domain.edition.entity.*;
import org.pluribourse.domain.edition.exception.*;
import org.pluribourse.domain.edition.mapper.*;
import org.pluribourse.domain.edition.repository.*;
import org.pluribourse.domain.instanceconfig.service.*;
import org.pluribourse.domain.item.repository.*;
import org.pluribourse.domain.pos.entity.*;
import org.pluribourse.domain.pos.repository.*;
import org.pluribourse.domain.user.enums.*;
import org.pluribourse.domain.user.repositories.*;
import org.pluribourse.shared.sse.*;
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
    private final ItemRepository itemRepository;
    private final EditionCategoryRepository editionCategoryRepository;
    private final BasketRepository basketRepository;
    private final UserRepository userRepository;

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

    /**
     * {@code hasItems} (story 2.7, AC 5) is only populated here, not by {@link #advancePhase}/
     * {@link #rollbackPhase}/the new closure/archiving endpoints — those keep returning it unset,
     * the frontend re-fetches via this method when it needs a fresh read.
     */
    @Transactional(readOnly = true)
    public EditionDto getEditionById(Long id) {
        Edition edition = findById(id);
        EditionDto dto = mapper.toDto(edition);
        boolean hasItems = itemRepository.existsByEditionId(id);
        return new EditionDto(dto.id(), dto.name(), dto.phase(), dto.commissionRate(), dto.documentLanguage(),
                dto.createdAt(), dto.archived(), dto.startDate(), dto.endDate(), hasItems, dto.currency());
    }

    /**
     * Edition summary report (story 5.4, FR-055): resolves by explicit ID rather than
     * {@link #getActiveEdition()}, whose {@code PhaseType.ACTIVE} filter excludes CLOSED — using
     * it here would make the CLOSED branch of {@code PhaseGuard.requirePostSaleOrClosedPhase}
     * unreachable, since the edition itself would no longer resolve once closed.
     */
    @Transactional(readOnly = true)
    public Edition requireEdition(Long id) {
        return findById(id);
    }

    @Transactional(readOnly = true)
    public Edition getActiveEdition() {
        return repository.findFirstByPhaseIn(PhaseType.ACTIVE)
                .orElseThrow(NoActiveEditionException::new);
    }

    @Transactional(readOnly = true)
    public EditionDto getActiveEditionDto() {
        return mapper.toDto(getActiveEdition());
    }

    @Transactional
    public EditionDto createEdition(EditionDto dto) {
        if (instanceConfigService.getAssociationName().isBlank()) {
            throw new AssociationNameNotConfiguredException();
        }
        BigDecimal commissionRate = dto.commissionRate() != null ? dto.commissionRate() : instanceConfigService.getDefaultCommissionRate();
        Language documentLanguage = dto.documentLanguage() != null ? dto.documentLanguage() : instanceConfigService.getDefaultDocumentLanguage();
        String currency = blankToNull(dto.currency()) != null ? dto.currency() : instanceConfigService.getDefaultCurrency();
        return mapper.toDto(repository.save(mapper.toEntity(dto, commissionRate, documentLanguage, currency)));
    }

    @Transactional
    public EditionDto updateEdition(Long id, EditionDto dto) {
        Edition edition = findById(id);
        if (edition.getPhase() != PhaseType.PREPARATION) {
            throw new EditionCannotBeUpdatedException();
        }
        // A blank (non-null) currency must be treated the same as an absent one — the mapper's
        // NullValuePropertyMappingStrategy.IGNORE only skips null, not blank strings, which would
        // otherwise silently wipe out a valid currency (see code review of story 2.9).
        EditionDto normalizedDto = dto.currency() != null && dto.currency().isBlank()
                ? new EditionDto(dto.id(), dto.name(), dto.phase(), dto.commissionRate(), dto.documentLanguage(),
                        dto.createdAt(), dto.archived(), dto.startDate(), dto.endDate(), dto.hasItems(), null)
                : dto;
        mapper.updateEditionFromDto(normalizedDto, edition);
        return mapper.toDto(repository.save(edition));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    @Transactional
    public void deleteEdition(Long id) {
        Edition edition = findById(id);
        if (edition.getPhase() != PhaseType.PREPARATION) {
            throw new EditionCannotBeDeletedException();
        }
        if (itemRepository.existsByEditionId(id)) {
            throw new EditionCannotBeDeletedException();
        }
        repository.delete(edition);
    }

    @Transactional
    public EditionDto advancePhase(Long id) {
        Edition edition = findById(id);
        PhaseType previousPhase = edition.getPhase();
        PhaseType newPhase = computeNextPhase(previousPhase);
        if (newPhase == PhaseType.DEPOSIT && repository.existsByPhaseIn(PhaseType.ACTIVE)) {
            throw new EditionAlreadyActiveException();
        }
        if (newPhase == PhaseType.DEPOSIT && !editionCategoryRepository.existsByEditionId(id)) {
            throw new NoCategoriesConfiguredException();
        }
        if (newPhase == PhaseType.DEPOSIT && !userRepository.existsByRole(Role.VOLUNTEER)) {
            throw new NoVolunteerConfiguredException();
        }
        return savePhaseThenSendEvent(id, edition, newPhase, previousPhase);
    }

    @Transactional
    public EditionDto rollbackPhase(Long id) {
        Edition edition = findById(id);
        PhaseType previousPhase = edition.getPhase();
        PhaseType newPhase = computePreviousPhase(previousPhase, edition.isArchived());
        return savePhaseThenSendEvent(id, edition, newPhase, previousPhase);
    }

    /**
     * Performs the POST_SALE → CLOSED transition directly, bypassing {@link #computeNextPhase}'s
     * refusal of that exact step. Package-private: the only caller is
     * {@link EditionClosingService#closeEdition}, which has already settled every remaining
     * UNSETTLED seller (FR-096) and checked the phase before calling this — {@link #advancePhase}
     * remains the only phase transition reachable from the generic /phase/advance endpoint.
     */
    @Transactional
    EditionDto closePostSaleToClosed(Long id) {
        Edition edition = findById(id);
        return savePhaseThenSendEvent(id, edition, PhaseType.CLOSED, edition.getPhase());
    }

    /**
     * Advances the phase state machine by one step.
     * POST_SALE has no successor here: closing an edition atomically settles every remaining
     * UNSETTLED seller as Non réclamé (FR-096), which only {@link EditionClosingService#closeEdition}
     * does — the generic advance endpoint must never reach CLOSED directly, or that step is skipped.
     * CLOSED itself has no successor either: closing is terminal and can only be undone via rollback.
     */
    private PhaseType computeNextPhase(PhaseType current) {
        return switch (current) {
            case PREPARATION -> PhaseType.DEPOSIT;
            case DEPOSIT -> PhaseType.SALE;
            case SALE -> PhaseType.POST_SALE;
            case POST_SALE -> throw new ClosingRequiresDedicatedEndpointException();
            case CLOSED -> throw new PhaseAlreadyClosedException();
        };
    }

    /**
     * Reverses the phase state machine by one step.
     * PREPARATION has no predecessor. Rollback from CLOSED is refused once the edition
     * is archived, since archiving is treated as a final, irreversible checkpoint.
     */
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

    /**
     * Persists the phase change, cancels any active POS basket for the edition (FR-090 —
     * a basket can only exist during the Sale phase, so it is always stale once the phase
     * changes, in either direction), and defers both SSE broadcasts to run only after the
     * transaction commits, so listeners never observe an event for a change that ends up
     * rolled back.
     */
    private EditionDto savePhaseThenSendEvent(Long id, Edition edition, PhaseType newPhase, PhaseType previousPhase) {
        edition.setPhase(newPhase);
        Edition saved = repository.save(edition);
        PhaseChangedEventDto phaseChangedEvent = new PhaseChangedEventDto(id, newPhase, previousPhase);

        List<Basket> activeBaskets = basketRepository.findAllByEditionId(id);
        BasketCancelledEventDto basketCancelledEvent = activeBaskets.isEmpty()
                ? null
                : new BasketCancelledEventDto(id, newPhase);
        if (!activeBaskets.isEmpty()) {
            basketRepository.deleteAll(activeBaskets);
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sseEmitterRegistry.broadcast("phase-changed", phaseChangedEvent);
                if (basketCancelledEvent != null) {
                    sseEmitterRegistry.broadcast("basket-cancelled", basketCancelledEvent);
                }
            }
        });
        return mapper.toDto(saved);
    }
}
