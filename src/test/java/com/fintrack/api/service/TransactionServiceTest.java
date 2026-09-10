package com.fintrack.api.service;

import com.fintrack.api.dto.TransactionRequest;
import com.fintrack.api.model.Transaction;
import com.fintrack.api.model.TransactionType;
import com.fintrack.api.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    private TransactionService transactionService;

    private TransactionRequest sampleRequest() {
        TransactionRequest request = new TransactionRequest();
        request.setAmount(new BigDecimal("25.50"));
        request.setType(TransactionType.EXPENSE);
        request.setCategory("Groceries");
        request.setDescription("Weekly shopping");
        request.setTransactionDate(LocalDate.of(2026, 1, 1));
        return request;
    }

    @Test
    void createTransaction_setsUserIdFromParameter_notFromRequestBody() {
        transactionService = new TransactionService(transactionRepository);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Transaction saved = transactionService.createTransaction(42L, sampleRequest());

        assertThat(saved.getUserId()).isEqualTo(42L);
        assertThat(saved.getAmount()).isEqualTo(new BigDecimal("25.50"));
        assertThat(saved.getCategory()).isEqualTo("Groceries");
    }

    @Test
    void getTransactionsByUser_queriesOnlyForRequestedUser_neverAnotherUser() {
        transactionService = new TransactionService(transactionRepository);
        Long userIdA = 1L;
        Long userIdB = 2L;
        Transaction ownTransaction = new Transaction();
        ownTransaction.setUserId(userIdA);
        when(transactionRepository.findByUserId(userIdA)).thenReturn(List.of(ownTransaction));

        List<Transaction> result = transactionService.getTransactionsByUser(userIdA);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(userIdA);
        verify(transactionRepository).findByUserId(eq(userIdA));
        verify(transactionRepository, never()).findByUserId(eq(userIdB));
    }

    @Test
    void deleteAllByUser_delegatesToRepositoryWithExactUserId() {
        transactionService = new TransactionService(transactionRepository);

        transactionService.deleteAllByUser(7L);

        ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
        verify(transactionRepository).deleteByUserId(captor.capture());
        assertThat(captor.getValue()).isEqualTo(7L);
    }

    @Test
    void createTransaction_throwsNullPointerException_whenUserIdMissing() {
        transactionService = new TransactionService(transactionRepository);

        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                () -> transactionService.createTransaction(null, sampleRequest()));
    }
}
