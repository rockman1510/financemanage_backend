package com.financemanage.identity.application;

/**
 * Outcome of the first sign-in step: either the tokens, or – when two-factor authentication is on – a challenge
 * to complete with a code.
 */
public record LoginResult(TokenPair tokens, String challengeToken, long challengeExpiresInSeconds) {

    static LoginResult signedIn(TokenPair tokens) {
        return new LoginResult(tokens, null, 0);
    }

    static LoginResult challenge(String challengeToken, long expiresInSeconds) {
        return new LoginResult(null, challengeToken, expiresInSeconds);
    }

    public boolean twoFactorRequired() {
        return challengeToken != null;
    }
}
