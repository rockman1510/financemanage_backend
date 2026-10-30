package com.financemanage.identity.domain;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Time-based one-time passwords (RFC 6238: HMAC-SHA1, 30-second steps, 6 digits), as used by Google
 * Authenticator, Authy, 1Password… Secrets are Base32 (RFC 4648, no padding).
 */
public final class Totp {

    public static final int PERIOD_SECONDS = 30;
    public static final int DIGITS = 6;
    /** Accept the previous and the next step too, to tolerate clock drift between phone and server. */
    private static final int WINDOW = 1;
    private static final int SECRET_BYTES = 20;
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Totp() {
    }

    public static String newSecret(SecureRandom random) {
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        return base32Encode(bytes);
    }

    public static long step(Instant now) {
        return Math.floorDiv(now.getEpochSecond(), PERIOD_SECONDS);
    }

    /**
     * The step matched by {@code code}, if it is valid around {@code now} and newer than {@code lastUsedStep}
     * (a code is accepted only once).
     */
    public static OptionalLong verify(String secret, String code, Instant now, Long lastUsedStep) {
        if (code == null || !code.matches("\\d{" + DIGITS + "}")) {
            return OptionalLong.empty();
        }
        long current = step(now);
        for (long step = current - WINDOW; step <= current + WINDOW; step++) {
            if ((lastUsedStep == null || step > lastUsedStep) && constantTimeEquals(code(secret, step), code)) {
                return OptionalLong.of(step);
            }
        }
        return OptionalLong.empty();
    }

    public static String code(String secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(base32Decode(secret), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            return String.format("%0" + DIGITS + "d", binary % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** URI to show as a QR code; authenticator apps read the account label, secret and issuer from it. */
    public static String otpauthUri(String issuer, String account, String secret) {
        String label = encode(issuer) + ":" + encode(account);
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + encode(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + PERIOD_SECONDS;
    }

    static String base32Encode(byte[] data) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(ALPHABET.charAt((buffer >> (bits - 5)) & 0x1f));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(ALPHABET.charAt((buffer << (5 - bits)) & 0x1f));
        }
        return out.toString();
    }

    static byte[] base32Decode(String text) {
        String clean = text.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        byte[] out = new byte[clean.length() * 5 / 8];
        int buffer = 0;
        int bits = 0;
        int index = 0;
        for (char c : clean.toCharArray()) {
            int value = ALPHABET.indexOf(c);
            if (value < 0) {
                throw new IllegalArgumentException("Invalid Base32 character " + c);
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                out[index++] = (byte) (buffer >> (bits - 8));
                bits -= 8;
            }
        }
        return out;
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII),
                b.getBytes(StandardCharsets.US_ASCII));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
