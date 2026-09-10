package com.fintrack.api.dto;

import com.fintrack.api.model.SplitType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request body for creating a {@link com.fintrack.api.model.SharedExpense}. The creator is never
 * accepted here — it is always derived server-side from the authenticated principal.
 */
public class CreateSharedExpenseRequest {

    @NotBlank
    @Size(max = 255)
    private String description;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    @Digits(integer = 17, fraction = 2)
    private BigDecimal totalAmount;

    @NotNull
    private SplitType splitType;

    @NotEmpty
    @Valid
    private List<ParticipantShareRequest> participants;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public SplitType getSplitType() {
        return splitType;
    }

    public void setSplitType(SplitType splitType) {
        this.splitType = splitType;
    }

    public List<ParticipantShareRequest> getParticipants() {
        return participants;
    }

    public void setParticipants(List<ParticipantShareRequest> participants) {
        this.participants = participants;
    }
}
