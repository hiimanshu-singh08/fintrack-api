package com.fintrack.api.service;

import com.fintrack.api.dto.TransactionRequest;
import com.fintrack.api.model.Transaction;
import com.fintrack.api.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class TransactionService {

    private static final Logger logger = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionRepository transactionRepository;

    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public Transaction createTransaction(Long userId, TransactionRequest request) {
        Objects.requireNonNull(userId, "userId is required");

        Transaction transaction = new Transaction();
        transaction.setUserId(userId);
        transaction.setAmount(request.getAmount());
        transaction.setType(request.getType());
        transaction.setCategory(request.getCategory());
        transaction.setDescription(request.getDescription());
        transaction.setTransactionDate(request.getTransactionDate());

        Transaction saved = transactionRepository.save(transaction);
        logger.info("Created transaction id={} for userId={}", saved.getId(), saved.getUserId());
        return saved;
    }

    public List<Transaction> getTransactionsByUser(Long userId) {
        Objects.requireNonNull(userId, "userId is required");
        return transactionRepository.findByUserId(userId);
    }

    @Transactional
    public void deleteAllByUser(Long userId) {
        Objects.requireNonNull(userId, "userId is required");
        transactionRepository.deleteByUserId(userId);
        logger.info("Deleted all transactions for userId={}", userId);
    }
}
