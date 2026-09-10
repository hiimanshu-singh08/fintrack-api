package com.fintrack.api.controller;

import com.fintrack.api.dto.TransactionRequest;
import com.fintrack.api.dto.TransactionResponse;
import com.fintrack.api.model.Transaction;
import com.fintrack.api.security.AppUserPrincipal;
import com.fintrack.api.service.TransactionService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private static final Logger logger = LoggerFactory.getLogger(TransactionController.class);

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> create(@Valid @RequestBody TransactionRequest request,
                                                        @AuthenticationPrincipal AppUserPrincipal principal) {
        logger.info("Create transaction request received for userId={}", principal.getId());
        Transaction saved = transactionService.createTransaction(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(TransactionResponse.fromEntity(saved));
    }

    @GetMapping
    public ResponseEntity<List<TransactionResponse>> getOwn(@AuthenticationPrincipal AppUserPrincipal principal) {
        List<TransactionResponse> transactions = transactionService.getTransactionsByUser(principal.getId())
                .stream()
                .map(TransactionResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(transactions);
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAllOwn(@AuthenticationPrincipal AppUserPrincipal principal) {
        logger.info("Delete-all transactions request for userId={}", principal.getId());
        transactionService.deleteAllByUser(principal.getId());
        return ResponseEntity.noContent().build();
    }
}
