package com.fintrack.api.dto;

public class AuthResponse {

    private final String token;
    private final String username;
    private final long expiresInMs;

    public AuthResponse(String token, String username, long expiresInMs) {
        this.token = token;
        this.username = username;
        this.expiresInMs = expiresInMs;
    }

    public String getToken() {
        return token;
    }

    public String getUsername() {
        return username;
    }

    public long getExpiresInMs() {
        return expiresInMs;
    }
}
