package com.financemanage.identity.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Random tokens and codes, and the SHA-256 hashes stored instead of them. */
final class Hashing {

    static final SecureRandom RANDOM = new SecureRandom();

    private Hashing() {
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 256-bit URL-safe random token. */
    static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** E.g. "042917" for {@code digits = 6}. */
    static String randomDigits(int digits) {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < digits; i++) {
            code.append(RANDOM.nextInt(10));
        }
        return code.toString();
    }
}
