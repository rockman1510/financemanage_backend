package com.financemanage.identity.application;

/** What the app shows to set up an authenticator: the secret (manual entry) and the otpauth URI (QR code). */
public record TwoFactorSetup(String secret, String otpauthUri) {
}
