package com.fintrack.api.dto;

import java.math.BigDecimal;

/**
 * Net pending balance between the authenticated user and a single counterparty, aggregated
 * across every {@link com.fintrack.api.model.SharedExpense} the two are both involved in.
 * Fully-settled counterparties (net amount of zero) are omitted from the result entirely.
 */
public class PendingBalanceResponse {

    private final Long counterpartyUserId;
    private final BigDecimal amount;
    private final BalanceDirection direction;

    public PendingBalanceResponse(Long counterpartyUserId, BigDecimal amount, BalanceDirection direction) {
        this.counterpartyUserId = counterpartyUserId;
        this.amount = amount;
        this.direction = direction;
    }

    public Long getCounterpartyUserId() {
        return counterpartyUserId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BalanceDirection getDirection() {
        return direction;
    }
}
