package org.pluribourse.shared.sse;

import org.pluribourse.domain.edition.entity.PhaseType;

public record BasketCancelledEventDto(Long editionId, PhaseType newPhase) {
}
