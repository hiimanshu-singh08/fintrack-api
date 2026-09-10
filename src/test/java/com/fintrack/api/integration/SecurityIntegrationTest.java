package com.fintrack.api.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String registerAndLogin(String username, String password) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        return body.get("token").asText();
    }

    @Test
    void userCannotSeeAnotherUsersTransactions() throws Exception {
        String tokenA = registerAndLogin("userA", "password-A-123");
        String tokenB = registerAndLogin("userB", "password-B-123");

        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType("application/json")
                        .content("{\"amount\":100.00,\"type\":\"INCOME\",\"transactionDate\":\"2026-01-01\"}"))
                .andExpect(status().isCreated());

        MvcResult userBResult = mockMvc.perform(get("/transactions")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode userBTransactions = objectMapper.readTree(userBResult.getResponse().getContentAsString());
        assertThat(userBTransactions.isArray()).isTrue();
        assertThat(userBTransactions).isEmpty();
    }

    @Test
    void requestWithoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/transactions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestWithTamperedToken_isUnauthorized() throws Exception {
        String token = registerAndLogin("userC", "password-C-123");
        String tampered = token.substring(0, token.length() - 3) + "abc";

        mockMvc.perform(get("/transactions")
                        .header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }
}
