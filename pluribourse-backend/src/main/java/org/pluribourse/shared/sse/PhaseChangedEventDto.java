package org.pluribourse.shared.sse;

import org.pluribourse.domain.edition.entity.PhaseType;

public record PhaseChangedEventDto(Long editionId, PhaseType newPhase, PhaseType previousPhase) {
}
