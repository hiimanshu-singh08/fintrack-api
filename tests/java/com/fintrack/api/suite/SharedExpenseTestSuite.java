package com.fintrack.api.suite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Acceptance suite for the Expense Splitting feature (JUnit 5, per CLAUDE.md's testing
 * standard), exercised end-to-end via real HTTP calls against the full Spring context and an
 * in-memory H2 database, exactly as the six specified cases.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SharedExpenseTestSuite {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Registers a fresh user with a random username and returns their auth token.
     */
    private String registerAndLogin(String usernamePrefix, String password) throws Exception {
        String username = usernamePrefix + "-" + UUID.randomUUID();

        mockMvc.perform(post("/auth/register")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("token").asText();
    }

    /**
     * Decodes the numeric user id embedded in a JWT's "userId" claim, without needing any
     * production bean wiring in the test.
     */
    private Long extractUserId(String token) throws Exception {
        String payload = token.split("\\.")[1];
        byte[] decoded = Base64.getUrlDecoder().decode(payload);
        JsonNode claims = objectMapper.readTree(decoded);
        return claims.get("userId").asLong();
    }

    private MvcResult createExpense(String token, String body) throws Exception {
        return mockMvc.perform(post("/shared-expenses")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body))
                .andReturn();
    }

    private JsonNode getBalances(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/shared-expenses/balances")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("1. Equal split among 3 participants")
    void equalSplitAmongThreeParticipants() throws Exception {
        String creatorToken = registerAndLogin("creator", "password-123");
        Long creatorId = extractUserId(creatorToken);
        String userBToken = registerAndLogin("userB", "password-123");
        Long userBId = extractUserId(userBToken);
        String userCToken = registerAndLogin("userC", "password-123");
        Long userCId = extractUserId(userCToken);

        String body = """
                {
                  "description": "Team lunch",
                  "totalAmount": 100.00,
                  "splitType": "EQUAL",
                  "participants": [{"userId": %d}, {"userId": %d}, {"userId": %d}]
                }
                """.formatted(creatorId, userBId, userCId);

        MvcResult result = createExpense(creatorToken, body);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode participants = response.get("participants");
        assertThat(participants).hasSize(3);

        BigDecimal sum = BigDecimal.ZERO;
        for (JsonNode participant : participants) {
            sum = sum.add(new BigDecimal(participant.get("shareAmount").asText()));
        }
        assertThat(sum).isEqualTo(new BigDecimal("100.00"));

        // The two non-creator shares of $100/3 must each owe the creator their exact share
        // ($33.33 - the creator's own entry, first in list order, absorbs the leftover cent).
        JsonNode balancesForB = getBalances(userBToken);
        assertThat(balancesForB).hasSize(1);
        assertThat(balancesForB.get(0).get("direction").asText()).isEqualTo("YOU_OWE");
        assertThat(new BigDecimal(balancesForB.get(0).get("amount").asText())).isEqualTo(new BigDecimal("33.33"));
    }

    @Test
    @DisplayName("2. Custom split with valid total")
    void customSplitWithValidTotal() throws Exception {
        String creatorToken = registerAndLogin("creator", "password-123");
        Long creatorId = extractUserId(creatorToken);
        String userBToken = registerAndLogin("userB", "password-123");
        Long userBId = extractUserId(userBToken);

        String body = """
                {
                  "description": "Groceries",
                  "totalAmount": 50.00,
                  "splitType": "CUSTOM",
                  "participants": [{"userId": %d, "shareAmount": 30.00}, {"userId": %d, "shareAmount": 20.00}]
                }
                """.formatted(creatorId, userBId);

        MvcResult result = createExpense(creatorToken, body);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);

        JsonNode balancesForB = getBalances(userBToken);
        assertThat(balancesForB).hasSize(1);
        assertThat(new BigDecimal(balancesForB.get(0).get("amount").asText())).isEqualTo(new BigDecimal("20.00"));
        assertThat(balancesForB.get(0).get("direction").asText()).isEqualTo("YOU_OWE");
    }

    @Test
    @DisplayName("3. Custom split that fails validation (sum != total)")
    void customSplitFailsValidationWhenSumDoesNotMatchTotal() throws Exception {
        String creatorToken = registerAndLogin("creator", "password-123");
        Long creatorId = extractUserId(creatorToken);
        String userBToken = registerAndLogin("userB", "password-123");
        Long userBId = extractUserId(userBToken);

        String body = """
                {
                  "description": "Groceries",
                  "totalAmount": 50.00,
                  "splitType": "CUSTOM",
                  "participants": [{"userId": %d, "shareAmount": 30.00}, {"userId": %d, "shareAmount": 15.00}]
                }
                """.formatted(creatorId, userBId);

        MvcResult result = createExpense(creatorToken, body);
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("4. Net balance calculation across multiple expenses")
    void netBalanceCalculationAcrossMultipleExpenses() throws Exception {
        String userAToken = registerAndLogin("userA", "password-123");
        Long userAId = extractUserId(userAToken);
        String userBToken = registerAndLogin("userB", "password-123");
        Long userBId = extractUserId(userBToken);

        // Expense 1: B pays, A owes B $30.
        createExpense(userBToken, """
                {
                  "description": "Concert tickets",
                  "totalAmount": 30.00,
                  "splitType": "CUSTOM",
                  "participants": [{"userId": %d, "shareAmount": 30.00}]
                }
                """.formatted(userAId))
                .getResponse();

        // Expense 2: A pays, B owes A $10.
        createExpense(userAToken, """
                {
                  "description": "Taxi",
                  "totalAmount": 10.00,
                  "splitType": "CUSTOM",
                  "participants": [{"userId": %d, "shareAmount": 10.00}]
                }
                """.formatted(userBId))
                .getResponse();

        // Net across both expenses: A owes B $30 - $10 = $20.
        JsonNode balancesForA = getBalances(userAToken);
        assertThat(balancesForA).hasSize(1);
        assertThat(balancesForA.get(0).get("counterpartyUserId").asLong()).isEqualTo(userBId);
        assertThat(new BigDecimal(balancesForA.get(0).get("amount").asText())).isEqualTo(new BigDecimal("20.00"));
        assertThat(balancesForA.get(0).get("direction").asText()).isEqualTo("YOU_OWE");

        JsonNode balancesForB = getBalances(userBToken);
        assertThat(balancesForB).hasSize(1);
        assertThat(balancesForB.get(0).get("counterpartyUserId").asLong()).isEqualTo(userAId);
        assertThat(new BigDecimal(balancesForB.get(0).get("amount").asText())).isEqualTo(new BigDecimal("20.00"));
        assertThat(balancesForB.get(0).get("direction").asText()).isEqualTo("THEY_OWE");
    }

    @Test
    @DisplayName("5. Edge case: expense with 1 participant")
    void expenseWithSingleParticipant() throws Exception {
        String creatorToken = registerAndLogin("creator", "password-123");
        Long creatorId = extractUserId(creatorToken);

        String body = """
                {
                  "description": "Solo purchase",
                  "totalAmount": 42.00,
                  "splitType": "EQUAL",
                  "participants": [{"userId": %d}]
                }
                """.formatted(creatorId);

        MvcResult result = createExpense(creatorToken, body);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.get("participants")).hasSize(1);
        assertThat(new BigDecimal(response.get("participants").get(0).get("shareAmount").asText()))
                .isEqualTo(new BigDecimal("42.00"));

        // The sole participant is the creator themselves, so no debt is generated.
        assertThat(getBalances(creatorToken)).isEmpty();
    }

    @Test
    @DisplayName("6. Unauthorized access attempt: a user cannot access another user's balance")
    void userCannotAccessAnotherUsersBalance() throws Exception {
        String userXToken = registerAndLogin("userX", "password-123");
        Long userXId = extractUserId(userXToken);
        String userYToken = registerAndLogin("userY", "password-123");
        Long userYId = extractUserId(userYToken);

        // X pays $50, split equally with Y, so Y owes X $25.
        createExpense(userXToken, """
                {
                  "description": "Shared cab",
                  "totalAmount": 50.00,
                  "splitType": "EQUAL",
                  "participants": [{"userId": %d}, {"userId": %d}]
                }
                """.formatted(userXId, userYId));

        // There is no endpoint or parameter that lets a caller name someone else's user id --
        // the balances endpoint always resolves the caller from their own verified JWT. Prove
        // each user only ever sees their own computed view, and that no token means no access.
        JsonNode balancesForX = getBalances(userXToken);
        assertThat(balancesForX).hasSize(1);
        assertThat(balancesForX.get(0).get("direction").asText()).isEqualTo("THEY_OWE");

        JsonNode balancesForY = getBalances(userYToken);
        assertThat(balancesForY).hasSize(1);
        assertThat(balancesForY.get(0).get("direction").asText()).isEqualTo("YOU_OWE");

        // An unauthenticated caller cannot reach anyone's balance at all.
        mockMvc.perform(get("/shared-expenses/balances"))
                .andExpect(status().isUnauthorized());

        // A tampered token (as if someone tried to forge another user's identity) is rejected too.
        String tamperedToken = userYToken.substring(0, userYToken.length() - 3) + "abc";
        mockMvc.perform(get("/shared-expenses/balances")
                        .header("Authorization", "Bearer " + tamperedToken))
                .andExpect(status().isUnauthorized());
    }
}
