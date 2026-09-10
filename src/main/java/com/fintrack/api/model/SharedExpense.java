package com.fintrack.api.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * An expense paid by one user ({@link #creatorId}) and shared across a list of
 * {@link ExpenseParticipant}s, each owing (or, for the creator's own entry, owning) a share of
 * {@link #totalAmount}.
 */
@Entity
@Table(name = "shared_expenses")
public class SharedExpense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long creatorId;

    @Column(length = 255, nullable = false)
    private String description;

    @Column(nullable = false)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SplitType splitType;

    @Column(nullable = false)
    private LocalDate createdDate;

    @OneToMany(mappedBy = "sharedExpense", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn(name = "participant_order")
    private List<ExpenseParticipant> participants = new ArrayList<>();

    public SharedExpense() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(Long creatorId) {
        this.creatorId = creatorId;
    }

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

    public LocalDate getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDate createdDate) {
        this.createdDate = createdDate;
    }

    public List<ExpenseParticipant> getParticipants() {
        return participants;
    }

    public void setParticipants(List<ExpenseParticipant> participants) {
        this.participants = participants;
    }

    /**
     * Adds a participant to this expense and wires the back-reference required for cascading
     * persistence.
     */
    public void addParticipant(ExpenseParticipant participant) {
        participant.setSharedExpense(this);
        this.participants.add(participant);
    }
}
