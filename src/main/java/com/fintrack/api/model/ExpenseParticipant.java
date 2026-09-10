package com.fintrack.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * One participant's share of a {@link SharedExpense}. A participant whose {@code userId} equals
 * the parent expense's {@code creatorId} represents the creator's own consumption and generates
 * no debt; every other participant owes {@link #shareAmount} to the creator.
 */
@Entity
@Table(name = "expense_participants")
public class ExpenseParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "shared_expense_id", nullable = false)
    private SharedExpense sharedExpense;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private BigDecimal shareAmount;

    public ExpenseParticipant() {
    }

    public ExpenseParticipant(Long userId, BigDecimal shareAmount) {
        this.userId = userId;
        this.shareAmount = shareAmount;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public SharedExpense getSharedExpense() {
        return sharedExpense;
    }

    public void setSharedExpense(SharedExpense sharedExpense) {
        this.sharedExpense = sharedExpense;
    }

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
