package com.fintrack.api.service;

import com.fintrack.api.dto.BalanceDirection;
import com.fintrack.api.dto.CreateSharedExpenseRequest;
import com.fintrack.api.dto.ParticipantShareRequest;
import com.fintrack.api.dto.PendingBalanceResponse;
import com.fintrack.api.exception.ResourceNotFoundException;
import com.fintrack.api.model.ExpenseParticipant;
import com.fintrack.api.model.SharedExpense;
import com.fintrack.api.model.SplitType;
import com.fintrack.api.repository.SharedExpenseRepository;
import com.fintrack.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SharedExpenseServiceTest {

    private static final Long CREATOR_ID = 1L;
    private static final Long USER_A = 1L;
    private static final Long USER_B = 2L;
    private static final Long USER_C = 3L;

    @Mock
    private SharedExpenseRepository sharedExpenseRepository;

    @Mock
    private UserRepository userRepository;

    private SharedExpenseService service() {
        return new SharedExpenseService(sharedExpenseRepository, userRepository);
    }

    private ParticipantShareRequest participant(Long userId, BigDecimal shareAmount) {
        ParticipantShareRequest p = new ParticipantShareRequest();
        p.setUserId(userId);
        p.setShareAmount(shareAmount);
        return p;
    }

    private CreateSharedExpenseRequest request(BigDecimal total, SplitType type, List<ParticipantShareRequest> participants) {
        CreateSharedExpenseRequest req = new CreateSharedExpenseRequest();
        req.setDescription("Dinner");
        req.setTotalAmount(total);
        req.setSplitType(type);
        req.setParticipants(participants);
        return req;
    }

    @Test
    void equalSplit_100dollars_3participants_distributesRemainderToFirstTwo() {
        SharedExpenseService service = service();
        when(userRepository.existsById(any())).thenReturn(true);
        when(sharedExpenseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateSharedExpenseRequest req = request(new BigDecimal("100.00"), SplitType.EQUAL,
                List.of(participant(1L, null), participant(2L, null), participant(3L, null)));

        SharedExpense saved = service.createSharedExpense(CREATOR_ID, req);

        List<BigDecimal> shares = saved.getParticipants().stream().map(ExpenseParticipant::getShareAmount).toList();
        assertThat(shares).containsExactly(new BigDecimal("33.34"), new BigDecimal("33.33"), new BigDecimal("33.33"));
        BigDecimal sum = shares.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualTo(new BigDecimal("100.00"));
    }

    @Test
    void equalSplit_10001dollars_3participants_distributesTwoRemainderCents() {
        SharedExpenseService service = service();
        when(userRepository.existsById(any())).thenReturn(true);
        when(sharedExpenseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateSharedExpenseRequest req = request(new BigDecimal("100.01"), SplitType.EQUAL,
                List.of(participant(1L, null), participant(2L, null), participant(3L, null)));

        SharedExpense saved = service.createSharedExpense(CREATOR_ID, req);

        List<BigDecimal> shares = saved.getParticipants().stream().map(ExpenseParticipant::getShareAmount).toList();
        assertThat(shares).containsExactly(new BigDecimal("33.34"), new BigDecimal("33.34"), new BigDecimal("33.33"));
    }

    @Test
    void equalSplit_singleParticipant_getsWholeAmount() {
        SharedExpenseService service = service();
        when(userRepository.existsById(any())).thenReturn(true);
        when(sharedExpenseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateSharedExpenseRequest req = request(new BigDecimal("42.00"), SplitType.EQUAL,
                List.of(participant(CREATOR_ID, null)));

        SharedExpense saved = service.createSharedExpense(CREATOR_ID, req);

        assertThat(saved.getParticipants().get(0).getShareAmount()).isEqualTo(new BigDecimal("42.00"));
    }

    @Test
    void customSplit_sumsExactly_succeeds() {
        SharedExpenseService service = service();
        when(userRepository.existsById(any())).thenReturn(true);
        when(sharedExpenseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateSharedExpenseRequest req = request(new BigDecimal("50.00"), SplitType.CUSTOM,
                List.of(participant(1L, new BigDecimal("30.00")), participant(2L, new BigDecimal("20.00"))));

        SharedExpense saved = service.createSharedExpense(CREATOR_ID, req);

        assertThat(saved.getParticipants()).hasSize(2);
    }

    @Test
    void customSplit_sumMismatch_throws() {
        SharedExpenseService service = service();
        when(userRepository.existsById(any())).thenReturn(true);

        CreateSharedExpenseRequest req = request(new BigDecimal("50.00"), SplitType.CUSTOM,
                List.of(participant(1L, new BigDecimal("30.00")), participant(2L, new BigDecimal("15.00"))));

        assertThatThrownBy(() -> service.createSharedExpense(CREATOR_ID, req))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void customSplit_missingShareAmount_throws() {
        SharedExpenseService service = service();
        when(userRepository.existsById(any())).thenReturn(true);

        CreateSharedExpenseRequest req = request(new BigDecimal("50.00"), SplitType.CUSTOM,
                List.of(participant(1L, new BigDecimal("50.00")), participant(2L, null)));

        assertThatThrownBy(() -> service.createSharedExpense(CREATOR_ID, req))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void duplicateParticipant_throws() {
        SharedExpenseService service = service();

        CreateSharedExpenseRequest req = request(new BigDecimal("50.00"), SplitType.EQUAL,
                List.of(participant(1L, null), participant(1L, null)));

        assertThatThrownBy(() -> service.createSharedExpense(CREATOR_ID, req))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownParticipant_throwsNotFound() {
        SharedExpenseService service = service();
        when(userRepository.existsById(eq(1L))).thenReturn(true);
        when(userRepository.existsById(eq(999L))).thenReturn(false);

        CreateSharedExpenseRequest req = request(new BigDecimal("50.00"), SplitType.EQUAL,
                List.of(participant(1L, null), participant(999L, null)));

        assertThatThrownBy(() -> service.createSharedExpense(CREATOR_ID, req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private SharedExpense expenseCreatedBy(Long creatorId, ExpenseParticipant... participants) {
        SharedExpense expense = new SharedExpense();
        expense.setCreatorId(creatorId);
        for (ExpenseParticipant p : participants) {
            expense.addParticipant(p);
        }
        return expense;
    }

    @Test
    void netBalances_aggregateAcrossExpenses_matchSpecExample() {
        SharedExpenseService service = service();

        // B created an expense where A owes B $30.
        SharedExpense expense1 = expenseCreatedBy(USER_B, new ExpenseParticipant(USER_A, new BigDecimal("30.00")));
        // A created an expense where B owes A $10.
        SharedExpense expense2 = expenseCreatedBy(USER_A, new ExpenseParticipant(USER_B, new BigDecimal("10.00")));

        when(sharedExpenseRepository.findInvolvingUser(USER_A)).thenReturn(List.of(expense1, expense2));
        when(sharedExpenseRepository.findInvolvingUser(USER_B)).thenReturn(List.of(expense1, expense2));

        List<PendingBalanceResponse> balancesForA = service.getBalancesForUser(USER_A);
        assertThat(balancesForA).hasSize(1);
        assertThat(balancesForA.get(0).getCounterpartyUserId()).isEqualTo(USER_B);
        assertThat(balancesForA.get(0).getAmount()).isEqualTo(new BigDecimal("20.00"));
        assertThat(balancesForA.get(0).getDirection()).isEqualTo(BalanceDirection.YOU_OWE);

        List<PendingBalanceResponse> balancesForB = service.getBalancesForUser(USER_B);
        assertThat(balancesForB).hasSize(1);
        assertThat(balancesForB.get(0).getCounterpartyUserId()).isEqualTo(USER_A);
        assertThat(balancesForB.get(0).getAmount()).isEqualTo(new BigDecimal("20.00"));
        assertThat(balancesForB.get(0).getDirection()).isEqualTo(BalanceDirection.THEY_OWE);
    }

    @Test
    void netBalances_fullySettledCounterparty_isOmitted() {
        SharedExpenseService service = service();

        SharedExpense expense1 = expenseCreatedBy(USER_B, new ExpenseParticipant(USER_A, new BigDecimal("30.00")));
        SharedExpense expense2 = expenseCreatedBy(USER_A, new ExpenseParticipant(USER_B, new BigDecimal("30.00")));

        when(sharedExpenseRepository.findInvolvingUser(USER_A)).thenReturn(List.of(expense1, expense2));

        List<PendingBalanceResponse> balances = service.getBalancesForUser(USER_A);
        assertThat(balances).isEmpty();
    }

    @Test
    void netBalances_unrelatedThirdPartyEdge_isSkipped() {
        SharedExpenseService service = service();

        // C created an expense involving both A and B as participants; this expense also
        // appears in A's "involving user" result set, but the B/C edge shouldn't leak into A's balances.
        SharedExpense expense = expenseCreatedBy(USER_C,
                new ExpenseParticipant(USER_A, new BigDecimal("10.00")),
                new ExpenseParticipant(USER_B, new BigDecimal("10.00")));

        when(sharedExpenseRepository.findInvolvingUser(USER_A)).thenReturn(List.of(expense));

        List<PendingBalanceResponse> balances = service.getBalancesForUser(USER_A);
        assertThat(balances).hasSize(1);
        assertThat(balances.get(0).getCounterpartyUserId()).isEqualTo(USER_C);
        assertThat(balances.get(0).getAmount()).isEqualTo(new BigDecimal("10.00"));
        assertThat(balances.get(0).getDirection()).isEqualTo(BalanceDirection.YOU_OWE);
    }
}
