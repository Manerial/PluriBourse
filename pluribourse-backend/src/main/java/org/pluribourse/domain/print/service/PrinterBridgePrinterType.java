package org.pluribourse.domain.print.service;

/**
 * Printer type as reported by PrinterBridge — a different vocabulary from PluriBourse's own
 * {@link org.pluribourse.domain.print.entity.PrinterType} ({@code THERMAL}/{@code A4}). Mapped
 * to the PluriBourse vocabulary in {@link PrinterService#discover()}.
 */
public enum PrinterBridgePrinterType {
    BLUETOOTH_THERMAL,
    NETWORK
}
