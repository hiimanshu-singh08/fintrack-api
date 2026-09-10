package com.fintrack.api.service;

import com.fintrack.api.model.Transaction;
import com.fintrack.api.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TransactionService {

    private static final Logger logger = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionRepository transactionRepository;

    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public Transaction createTransaction(Transaction transaction) {
        if (transaction.getUserId() == null) {
            throw new IllegalArgumentException("userId is required");
        }
        Transaction saved = transactionRepository.save(transaction);
        logger.info("Created transaction {} for user {}", saved.getId(), saved.getUserId());
        return saved;
    }

    public List<Transaction> getTransactionsByUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        return transactionRepository.findByUserId(userId);
    }

    @Transactional
    public void deleteAllByUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        transactionRepository.deleteByUserId(userId);
        logger.info("Deleted all transactions for user {}", userId);
    }
}
