package com.fintrack.api.controller;

import com.fintrack.api.dto.BalanceDirection;
import com.fintrack.api.dto.PendingBalanceResponse;
import com.fintrack.api.model.ExpenseParticipant;
import com.fintrack.api.model.Role;
import com.fintrack.api.model.SharedExpense;
import com.fintrack.api.model.SplitType;
import com.fintrack.api.model.User;
import com.fintrack.api.security.AppUserPrincipal;
import com.fintrack.api.service.SharedExpenseService;
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
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SharedExpenseController.class)
@AutoConfigureMockMvc(addFilters = false)
class SharedExpenseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SharedExpenseService sharedExpenseService;

    private static final Long USER_ID = 10L;

    private RequestPostProcessor asUser(Long userId) {
        User user = new User();
        user.setId(userId);
        user.setUsername("alice");
        user.setPassword("hash");
        user.setRole(Role.USER);
        AppUserPrincipal principal = new AppUserPrincipal(user);
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return authentication(authentication);
    }

    @Test
    void create_returns201_forValidEqualSplit() throws Exception {
        SharedExpense saved = new SharedExpense();
        saved.setId(1L);
        saved.setCreatorId(USER_ID);
        saved.setDescription("Dinner");
        saved.setTotalAmount(new BigDecimal("50.00"));
        saved.setSplitType(SplitType.EQUAL);
        saved.setCreatedDate(LocalDate.of(2026, 1, 1));
        saved.addParticipant(new ExpenseParticipant(20L, new BigDecimal("25.00")));
        saved.addParticipant(new ExpenseParticipant(30L, new BigDecimal("25.00")));
        when(sharedExpenseService.createSharedExpense(eq(USER_ID), any())).thenReturn(saved);

        mockMvc.perform(post("/shared-expenses")
                        .with(asUser(USER_ID))
                        .contentType("application/json")
                        .content("""
                                {
                                  "description": "Dinner",
                                  "totalAmount": 50.00,
                                  "splitType": "EQUAL",
                                  "participants": [{"userId": 20}, {"userId": 30}]
                                }
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void create_returns400_whenCustomSplitDoesNotSumToTotal() throws Exception {
        when(sharedExpenseService.createSharedExpense(eq(USER_ID), any()))
                .thenThrow(new IllegalArgumentException("Participant shares must sum exactly to totalAmount"));

        mockMvc.perform(post("/shared-expenses")
                        .with(asUser(USER_ID))
                        .contentType("application/json")
                        .content("""
                                {
                                  "description": "Dinner",
                                  "totalAmount": 50.00,
                                  "splitType": "CUSTOM",
                                  "participants": [{"userId": 20, "shareAmount": 30.00}, {"userId": 30, "shareAmount": 15.00}]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_returns400_whenParticipantsEmpty() throws Exception {
        mockMvc.perform(post("/shared-expenses")
                        .with(asUser(USER_ID))
                        .contentType("application/json")
                        .content("""
                                {
                                  "description": "Dinner",
                                  "totalAmount": 50.00,
                                  "splitType": "EQUAL",
                                  "participants": []
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getBalances_returns200_withCurrentUsersBalances() throws Exception {
        when(sharedExpenseService.getBalancesForUser(USER_ID)).thenReturn(
                List.of(new PendingBalanceResponse(20L, new BigDecimal("20.00"), BalanceDirection.YOU_OWE)));

        mockMvc.perform(get("/shared-expenses/balances").with(asUser(USER_ID)))
                .andExpect(status().isOk());
    }
}
