package org.pluribourse.domain.print.entity;

/**
 * Wire-format content type sent to PrinterBridge over {@code WS /printers/{id}/print} (story
 * 3.12) — names match PrinterBridge's own {@code PrintContentType} enum exactly (Jackson
 * serializes by name). Distinct from {@link PrinterType} ({@code THERMAL}/{@code A4}, the
 * registered printer's category).
 */
public enum PrintContentType {
    ESC_POS,
    PDF
}
