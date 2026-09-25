package com.rtz.ordership.dto.response;

public record LoginResponse(
        String token,
        String type,
        String refreshToken,
        UserResponse user) {
    public LoginResponse(String token, String refreshToken, UserResponse user) {
        this(token, "Bearer", refreshToken, user);
    }
}
