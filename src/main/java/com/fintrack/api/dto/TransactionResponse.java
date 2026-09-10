package com.fintrack.api.dto;

import com.fintrack.api.model.Transaction;
import com.fintrack.api.model.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;

public class TransactionResponse {

    private final Long id;
    private final BigDecimal amount;
    private final TransactionType type;
    private final String category;
    private final String description;
    private final LocalDate transactionDate;

    public TransactionResponse(Long id, BigDecimal amount, TransactionType type, String category,
                                String description, LocalDate transactionDate) {
        this.id = id;
        this.amount = amount;
        this.type = type;
        this.category = category;
        this.description = description;
        this.transactionDate = transactionDate;
    }

    public static TransactionResponse fromEntity(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getAmount(),
                transaction.getType(),
                transaction.getCategory(),
                transaction.getDescription(),
                transaction.getTransactionDate());
    }

    public Long getId() {
        return id;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransactionType getType() {
        return type;
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }
}
