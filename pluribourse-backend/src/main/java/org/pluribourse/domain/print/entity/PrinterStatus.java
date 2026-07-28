package org.pluribourse.domain.print.entity;

/**
 * Connectivity status as reported by PrinterBridge for a single printer — distinct from
 * PrinterBridge itself being unreachable (see {@code PrinterBridgeUnavailableException}).
 */
public enum PrinterStatus {
    ONLINE,
    OFFLINE,
    UNKNOWN
}
