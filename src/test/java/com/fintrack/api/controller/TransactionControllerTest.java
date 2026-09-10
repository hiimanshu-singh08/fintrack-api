package com.fintrack.api.controller;

import com.fintrack.api.model.Role;
import com.fintrack.api.model.Transaction;
import com.fintrack.api.model.TransactionType;
import com.fintrack.api.model.User;
import com.fintrack.api.security.AppUserPrincipal;
import com.fintrack.api.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
@AutoConfigureMockMvc(addFilters = false)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransactionService transactionService;

    private static final Long USER_ID = 10L;
    private static final Long OTHER_USER_ID = 20L;

    private RequestPostProcessor asUser(Long userId, String username) {
        User user = new User();
        user.setId(userId);
        user.setUsername(username);
        user.setPassword("hash");
        user.setRole(Role.USER);
        AppUserPrincipal principal = new AppUserPrincipal(user);
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return authentication(authentication);
    }

    @Test
    void create_returns201_andUsesAuthenticatedUserId() throws Exception {
        Transaction saved = new Transaction();
        saved.setId(1L);
        saved.setUserId(USER_ID);
        saved.setAmount(new BigDecimal("10.00"));
        saved.setType(TransactionType.EXPENSE);
        saved.setTransactionDate(LocalDate.of(2026, 1, 1));
        when(transactionService.createTransaction(eq(USER_ID), any())).thenReturn(saved);

        mockMvc.perform(post("/transactions")
                        .with(asUser(USER_ID, "alice"))
                        .contentType("application/json")
                        .content("{\"amount\":10.00,\"type\":\"EXPENSE\",\"transactionDate\":\"2026-01-01\"}"))
                .andExpect(status().isCreated());

        verify(transactionService).createTransaction(eq(USER_ID), any());
    }

    @Test
    void create_returns400_onInvalidAmount() throws Exception {
        mockMvc.perform(post("/transactions")
                        .with(asUser(USER_ID, "alice"))
                        .contentType("application/json")
                        .content("{\"amount\":-5.00,\"type\":\"EXPENSE\",\"transactionDate\":\"2026-01-01\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getOwn_queriesOnlyAuthenticatedUsersId_neverAnotherUsersId() throws Exception {
        when(transactionService.getTransactionsByUser(USER_ID)).thenReturn(List.of());

        mockMvc.perform(get("/transactions").with(asUser(USER_ID, "alice")))
                .andExpect(status().isOk());

        verify(transactionService).getTransactionsByUser(eq(USER_ID));
        verify(transactionService, never()).getTransactionsByUser(eq(OTHER_USER_ID));
    }

    @Test
    void deleteAllOwn_returns204_andDelegatesWithAuthenticatedUserId() throws Exception {
        mockMvc.perform(delete("/transactions").with(asUser(USER_ID, "alice")))
                .andExpect(status().isNoContent());

        verify(transactionService).deleteAllByUser(eq(USER_ID));
    }
}
