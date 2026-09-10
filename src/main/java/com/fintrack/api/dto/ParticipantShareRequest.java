package com.fintrack.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * One participant entry in a {@link CreateSharedExpenseRequest}. {@code shareAmount} is required
 * only when the parent request's split type is {@code CUSTOM}; for {@code EQUAL} splits any
 * supplied value is ignored and recomputed server-side. This conditional requirement is enforced
 * in {@code SharedExpenseService}, since Bean Validation can't express "required only if a
 * sibling field on the parent DTO equals X" declaratively.
 */
public class ParticipantShareRequest {

    @NotNull
    private Long userId;

    @DecimalMin(value = "0.0", inclusive = true)
    @Digits(integer = 17, fraction = 2)
    private BigDecimal shareAmount;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public BigDecimal getShareAmount() {
        return shareAmount;
    }

    public void setShareAmount(BigDecimal shareAmount) {
        this.shareAmount = shareAmount;
    }
}
