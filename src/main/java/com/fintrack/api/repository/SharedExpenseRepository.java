package com.fintrack.api.repository;

import com.fintrack.api.model.SharedExpense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SharedExpenseRepository extends JpaRepository<SharedExpense, Long> {

    /**
     * Fetches every {@link SharedExpense} the given user is involved in, either as the creator
     * or as any participant, with participants eagerly fetched. The filter is applied via a
     * subquery rather than on the fetch-joined collection itself, so an expense's other
     * participants are never dropped from the result.
     */
    @Query("""
            SELECT DISTINCT se FROM SharedExpense se
            LEFT JOIN FETCH se.participants
            WHERE se.creatorId = :userId
               OR se.id IN (SELECT p.sharedExpense.id FROM ExpenseParticipant p WHERE p.userId = :userId)
            ORDER BY se.createdDate DESC
            """)
    List<SharedExpense> findInvolvingUser(@Param("userId") Long userId);
}
