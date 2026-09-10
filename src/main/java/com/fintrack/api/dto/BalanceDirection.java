package com.fintrack.api.dto;

/**
 * Direction of a net {@link PendingBalanceResponse} between the authenticated user and a
 * counterparty. Purely a computed presentation concept — never persisted.
 */
public enum BalanceDirection {
    YOU_OWE,
    THEY_OWE
}
