package org.pluribourse.domain.print.dto;

import org.pluribourse.domain.print.entity.PrinterType;

public record PrinterStatusDto(
        Long id,
        String name,
        PrinterType type,
        boolean connected,
        int queueDepth,
        boolean jobInProgress,
        String lastError,
        boolean canRetry
) {
}
