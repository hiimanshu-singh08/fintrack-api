package com.fintrack.api.controller;

import com.fintrack.api.dto.CreateSharedExpenseRequest;
import com.fintrack.api.dto.PendingBalanceResponse;
import com.fintrack.api.dto.SharedExpenseResponse;
import com.fintrack.api.model.SharedExpense;
import com.fintrack.api.security.AppUserPrincipal;
import com.fintrack.api.service.SharedExpenseService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoints for creating shared expenses and viewing net pending balances. The current user is
 * always resolved from the authenticated JWT principal, never from a client-supplied id, so a
 * user can only ever create expenses as themselves and only ever view their own balances.
 */
@RestController
@RequestMapping("/shared-expenses")
public class SharedExpenseController {

    private static final Logger logger = LoggerFactory.getLogger(SharedExpenseController.class);

    private final SharedExpenseService sharedExpenseService;

    public SharedExpenseController(SharedExpenseService sharedExpenseService) {
        this.sharedExpenseService = sharedExpenseService;
    }

    /**
     * Creates a new shared expense paid by the authenticated user.
     */
    @PostMapping
    public ResponseEntity<SharedExpenseResponse> create(@Valid @RequestBody CreateSharedExpenseRequest request,
                                                          @AuthenticationPrincipal AppUserPrincipal principal) {
        logger.info("Create shared expense request received for creatorId={}", principal.getId());
        SharedExpense saved = sharedExpenseService.createSharedExpense(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(SharedExpenseResponse.fromEntity(saved));
    }

    /**
     * Returns the authenticated user's net pending balance with every counterparty they share
     * at least one expense with. Note: this literal "/balances" segment is matched before any
     * future {@code GET /shared-expenses/{id}} path variable would be added — keep that in mind
     * if such an endpoint is introduced later.
     */
    @GetMapping("/balances")
    public ResponseEntity<List<PendingBalanceResponse>> getBalances(@AuthenticationPrincipal AppUserPrincipal principal) {
        return ResponseEntity.ok(sharedExpenseService.getBalancesForUser(principal.getId()));
    }
}
