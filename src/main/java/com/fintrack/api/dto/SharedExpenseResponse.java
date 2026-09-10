package com.fintrack.api.dto;

import com.fintrack.api.model.SharedExpense;
import com.fintrack.api.model.SplitType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Output representation of a persisted {@link SharedExpense}, including its resolved
 * participant shares.
 */
public class SharedExpenseResponse {

    private final Long id;
    private final Long creatorId;
    private final String description;
    private final BigDecimal totalAmount;
    private final SplitType splitType;
    private final LocalDate createdDate;
    private final List<ParticipantShareResponse> participants;

    public SharedExpenseResponse(Long id, Long creatorId, String description, BigDecimal totalAmount,
                                  SplitType splitType, LocalDate createdDate,
                                  List<ParticipantShareResponse> participants) {
        this.id = id;
        this.creatorId = creatorId;
        this.description = description;
        this.totalAmount = totalAmount;
        this.splitType = splitType;
        this.createdDate = createdDate;
        this.participants = participants;
    }

    /**
     * Builds a response DTO from a persisted {@link SharedExpense} entity.
     */
    public static SharedExpenseResponse fromEntity(SharedExpense expense) {
        List<ParticipantShareResponse> participants = expense.getParticipants().stream()
                .map(ParticipantShareResponse::fromEntity)
                .toList();
        return new SharedExpenseResponse(expense.getId(), expense.getCreatorId(), expense.getDescription(),
                expense.getTotalAmount(), expense.getSplitType(), expense.getCreatedDate(), participants);
    }

    public Long getId() {
        return id;
    }

    public Long getCreatorId() {
        return creatorId;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public SplitType getSplitType() {
        return splitType;
    }

    public LocalDate getCreatedDate() {
        return createdDate;
    }

    public List<ParticipantShareResponse> getParticipants() {
        return participants;
    }
}
