package org.pluribourse.domain.print.service;

/**
 * Deserialized form of PrinterBridge's {@code PrintResult} JSON — returned by both
 * {@code POST /printers/{id}/test-print} and, in Story 3.12, the {@code WS /printers/{id}/print}
 * result message.
 */
public record PrintResult(PrintResultStatus status, String message) {
}
