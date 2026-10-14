package com.payflow.auth.dto;

public record TokenResponse(String accessToken, String tokenType, long expiresInSeconds, UserResponse user) {
}
