package com.financemanage.identity.application;

public record TokenPair(String accessToken, String refreshToken, long expiresInSeconds) {
}
