package org.pluribourse.domain.print.dto;

import jakarta.validation.constraints.Positive;

/**
 * Doubles as the selection request body ({@code done} is ignored on input, the caller never
 * sets it) and the status response ({@code done} then reflects whether a selection was
 * submitted for the current session) — see {@code PrinterSelectionService}. {@code Boolean}
 * rather than {@code boolean}: Jackson's record deserialization rejects a request body that
 * omits a primitive component (no JSON value to bind it to), but happily defaults a missing
 * boxed component to {@code null}.
 */
public record PrinterSelectionDto(
        @Positive Long thermalPrinterId,
        @Positive Long a4PrinterId,
        Boolean done
) {
}
