package com.fintrack.api.dto;

import com.fintrack.api.model.ExpenseParticipant;

import java.math.BigDecimal;

/**
 * Output representation of a single {@link ExpenseParticipant}.
 */
public class ParticipantShareResponse {

    private final Long userId;
    private final BigDecimal shareAmount;

    public ParticipantShareResponse(Long userId, BigDecimal shareAmount) {
        this.userId = userId;
        this.shareAmount = shareAmount;
    }

    /**
     * Builds a response DTO from a persisted {@link ExpenseParticipant} entity.
     */
    public static ParticipantShareResponse fromEntity(ExpenseParticipant participant) {
        return new ParticipantShareResponse(participant.getUserId(), participant.getShareAmount());
    }

    public Long getUserId() {
        return userId;
    }

    public BigDecimal getShareAmount() {
        return shareAmount;
    }
}
