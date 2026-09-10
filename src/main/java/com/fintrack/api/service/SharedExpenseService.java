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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Business logic for creating shared expenses and computing net pending balances between users.
 */
@Service
public class SharedExpenseService {

    private static final Logger logger = LoggerFactory.getLogger(SharedExpenseService.class);
    private static final int CURRENCY_SCALE = 2;
    private static final BigDecimal CENT = new BigDecimal("0.01");

    private final SharedExpenseRepository sharedExpenseRepository;
    private final UserRepository userRepository;

    public SharedExpenseService(SharedExpenseRepository sharedExpenseRepository, UserRepository userRepository) {
        this.sharedExpenseRepository = sharedExpenseRepository;
        this.userRepository = userRepository;
    }

    /**
     * Creates a shared expense paid by {@code creatorId}, validating and resolving participant
     * shares according to the request's {@link SplitType}.
     *
     * @param creatorId the authenticated user creating the expense; never trusted from the request body
     * @param request   the validated request body
     * @return the persisted {@link SharedExpense}, with resolved participant shares
     * @throws IllegalArgumentException  if participants contain duplicates, a CUSTOM split is
     *                                    missing a share amount, or custom shares don't sum exactly to the total
     * @throws ResourceNotFoundException if any participant userId does not correspond to an existing user
     */
    public SharedExpense createSharedExpense(Long creatorId, CreateSharedExpenseRequest request) {
        Objects.requireNonNull(creatorId, "creatorId is required");

        List<ParticipantShareRequest> participantRequests = request.getParticipants();
        rejectDuplicateParticipants(participantRequests);
        validateParticipantsExist(participantRequests);

        List<BigDecimal> resolvedShares = request.getSplitType() == SplitType.CUSTOM
                ? resolveCustomShares(participantRequests, request.getTotalAmount())
                : resolveEqualShares(participantRequests.size(), request.getTotalAmount());

        SharedExpense expense = new SharedExpense();
        expense.setCreatorId(creatorId);
        expense.setDescription(request.getDescription());
        expense.setTotalAmount(request.getTotalAmount());
        expense.setSplitType(request.getSplitType());
        expense.setCreatedDate(LocalDate.now());

        for (int i = 0; i < participantRequests.size(); i++) {
            Long userId = participantRequests.get(i).getUserId();
            expense.addParticipant(new ExpenseParticipant(userId, resolvedShares.get(i)));
        }

        SharedExpense saved = sharedExpenseRepository.save(expense);
        logger.info("Created shared expense id={} for creatorId={} with {} participants",
                saved.getId(), creatorId, participantRequests.size());
        return saved;
    }

    /**
     * Computes net pending balances between {@code userId} and every counterparty they share at
     * least one {@link SharedExpense} with, aggregated across all such expenses. Counterparties
     * that are fully settled (net amount of zero) are omitted.
     *
     * @param userId the authenticated user whose balances are being computed
     * @return one entry per counterparty with an outstanding net balance
     */
    @Transactional(readOnly = true)
    public List<PendingBalanceResponse> getBalancesForUser(Long userId) {
        Objects.requireNonNull(userId, "userId is required");

        // Positive => counterparty owes userId. Negative => userId owes counterparty.
        Map<Long, BigDecimal> net = new HashMap<>();

        for (SharedExpense expense : sharedExpenseRepository.findInvolvingUser(userId)) {
            Long creatorId = expense.getCreatorId();
            for (ExpenseParticipant participant : expense.getParticipants()) {
                Long debtorId = participant.getUserId();
                if (debtorId.equals(creatorId)) {
                    continue; // creator's own share, not a debt
                }
                if (debtorId.equals(userId)) {
                    net.merge(creatorId, participant.getShareAmount().negate(), BigDecimal::add);
                } else if (creatorId.equals(userId)) {
                    net.merge(debtorId, participant.getShareAmount(), BigDecimal::add);
                }
                // else: this debt edge doesn't touch userId directly; it is accounted for when
                // computing balances for that other participant/creator instead.
            }
        }

        List<PendingBalanceResponse> balances = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : net.entrySet()) {
            BigDecimal amount = entry.getValue();
            if (amount.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            BalanceDirection direction = amount.compareTo(BigDecimal.ZERO) < 0
                    ? BalanceDirection.YOU_OWE
                    : BalanceDirection.THEY_OWE;
            balances.add(new PendingBalanceResponse(entry.getKey(), amount.abs(), direction));
        }
        balances.sort((a, b) -> Long.compare(a.getCounterpartyUserId(), b.getCounterpartyUserId()));
        return balances;
    }

    private void rejectDuplicateParticipants(List<ParticipantShareRequest> participants) {
        Set<Long> seen = new HashSet<>();
        for (ParticipantShareRequest participant : participants) {
            if (!seen.add(participant.getUserId())) {
                throw new IllegalArgumentException("Duplicate participant userId: " + participant.getUserId());
            }
        }
    }

    private void validateParticipantsExist(List<ParticipantShareRequest> participants) {
        for (ParticipantShareRequest participant : participants) {
            if (!userRepository.existsById(participant.getUserId())) {
                throw new ResourceNotFoundException("User not found: " + participant.getUserId());
            }
        }
    }

    private List<BigDecimal> resolveCustomShares(List<ParticipantShareRequest> participants, BigDecimal totalAmount) {
        BigDecimal sum = BigDecimal.ZERO;
        List<BigDecimal> shares = new ArrayList<>(participants.size());
        for (ParticipantShareRequest participant : participants) {
            BigDecimal share = participant.getShareAmount();
            if (share == null) {
                throw new IllegalArgumentException(
                        "shareAmount is required for every participant in a CUSTOM split");
            }
            shares.add(share);
            sum = sum.add(share);
        }
        if (sum.compareTo(totalAmount) != 0) {
            throw new IllegalArgumentException(
                    "Participant shares must sum exactly to totalAmount (expected " + totalAmount + ", got " + sum + ")");
        }
        return shares;
    }

    /**
     * Splits {@code totalAmount} evenly across {@code participantCount} participants using the
     * largest-remainder method: everyone gets the amount floored to the cent, and the leftover
     * cents (always fewer than the participant count) are distributed one cent at a time to
     * participants in list order, so the shares always sum exactly to the total.
     */
    private List<BigDecimal> resolveEqualShares(int participantCount, BigDecimal totalAmount) {
        BigDecimal baseShare = totalAmount.divide(BigDecimal.valueOf(participantCount), CURRENCY_SCALE, RoundingMode.DOWN);
        BigDecimal distributed = baseShare.multiply(BigDecimal.valueOf(participantCount));
        int remainderCents = totalAmount.subtract(distributed)
                .divide(CENT, 0, RoundingMode.HALF_UP)
                .intValueExact();

        List<BigDecimal> shares = new ArrayList<>(participantCount);
        for (int i = 0; i < participantCount; i++) {
            shares.add(i < remainderCents ? baseShare.add(CENT) : baseShare);
        }
        return shares;
    }
}
