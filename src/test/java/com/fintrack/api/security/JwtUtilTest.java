package com.fintrack.api.security;

import com.fintrack.api.model.Role;
import com.fintrack.api.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private static final String SECRET = "test-secret-key-for-jwt-signing-must-be-32-bytes-min";

    private JwtUtil jwtUtil;
    private AppUserPrincipal principal;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, 60_000L);
        User user = new User();
        user.setId(99L);
        user.setUsername("carol");
        user.setPassword("irrelevant-hash");
        user.setRole(Role.USER);
        principal = new AppUserPrincipal(user);
    }

    @Test
    void generateToken_roundTripsUsernameAndUserId() {
        String token = jwtUtil.generateToken(principal);

        assertThat(jwtUtil.extractUsername(token)).isEqualTo("carol");
        assertThat(jwtUtil.extractUserId(token)).isEqualTo(99L);
    }

    @Test
    void isTokenValid_true_forMatchingFreshToken() {
        String token = jwtUtil.generateToken(principal);

        assertThat(jwtUtil.isTokenValid(token, principal)).isTrue();
    }

    @Test
    void isTokenValid_false_whenExpired() throws InterruptedException {
        JwtUtil shortLivedJwtUtil = new JwtUtil(SECRET, 1L);
        String token = shortLivedJwtUtil.generateToken(principal);

        Thread.sleep(20);

        assertThat(shortLivedJwtUtil.isTokenValid(token, principal)).isFalse();
    }

    @Test
    void isTokenValid_false_whenSignatureTampered() {
        String token = jwtUtil.generateToken(principal);
        String tampered = token.substring(0, token.length() - 2) + "xx";

        UserDetails details = principal;
        assertThat(jwtUtil.isTokenValid(tampered, details)).isFalse();
    }
}
