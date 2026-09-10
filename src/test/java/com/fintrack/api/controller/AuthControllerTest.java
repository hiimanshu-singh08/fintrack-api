package com.fintrack.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.api.exception.UsernameAlreadyExistsException;
import com.fintrack.api.model.Role;
import com.fintrack.api.model.User;
import com.fintrack.api.security.AppUserPrincipal;
import com.fintrack.api.security.JwtUtil;
import com.fintrack.api.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private AuthenticationManager authenticationManager;

    @MockBean
    private JwtUtil jwtUtil;

    @Test
    void register_returns201_onSuccess() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType("application/json")
                        .content("{\"username\":\"newuser\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void register_returns409_whenUsernameTaken() throws Exception {
        when(userService.registerUser(any())).thenThrow(new UsernameAlreadyExistsException("taken"));

        mockMvc.perform(post("/auth/register")
                        .contentType("application/json")
                        .content("{\"username\":\"taken\",\"password\":\"password123\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void register_returns400_onMissingFields() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType("application/json")
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_returns200WithToken_onValidCredentials() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setUsername("existing");
        user.setPassword("hash");
        user.setRole(Role.USER);
        AppUserPrincipal principal = new AppUserPrincipal(user);
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(jwtUtil.generateToken(any())).thenReturn("signed-jwt-token");
        when(jwtUtil.getExpirationMs()).thenReturn(3600000L);

        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"existing\",\"password\":\"correct-password\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void login_returns401_onBadCredentials() throws Exception {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad creds"));

        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"existing\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
    }
}
