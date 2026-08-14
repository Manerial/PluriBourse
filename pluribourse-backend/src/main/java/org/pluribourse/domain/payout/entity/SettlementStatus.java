package org.pluribourse.domain.payout.entity;

/**
 * {@code UNSETTLED} is never persisted — it only represents the absence of a {@link Settlement}
 * row for a seller, computed at the service level.
 */
public enum SettlementStatus {
    UNSETTLED,
    SETTLED,
    UNCLAIMED
}
