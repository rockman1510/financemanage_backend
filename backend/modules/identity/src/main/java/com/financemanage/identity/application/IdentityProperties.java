package com.financemanage.identity.application;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunable rules of the identity module ({@code app.identity.*}); every value has a safe default.
 *
 * @param login        account lock after repeated wrong passwords
 * @param verification one-time codes sent by email
 * @param twoFactor    TOTP two-factor authentication
 * @param google       Google sign-in; disabled while no client id is configured
 * @param mail         sender address of emails
 */
@ConfigurationProperties("app.identity")
public record IdentityProperties(Login login, Verification verification, TwoFactor twoFactor, Google google,
                                 Mail mail) {

    public IdentityProperties {
        login = login == null ? new Login(0, null) : login;
        verification = verification == null ? new Verification(null, 0, null) : verification;
        twoFactor = twoFactor == null ? new TwoFactor(null, null, 0) : twoFactor;
        google = google == null ? new Google(null) : google;
        mail = mail == null ? new Mail(null) : mail;
    }

    public record Login(int maxAttempts, Duration lockDuration) {
        public Login {
            maxAttempts = maxAttempts <= 0 ? 5 : maxAttempts;
            lockDuration = lockDuration == null ? Duration.ofMinutes(15) : lockDuration;
        }
    }

    public record Verification(Duration codeTtl, int maxAttempts, Duration resendCooldown) {
        public Verification {
            codeTtl = codeTtl == null ? Duration.ofMinutes(15) : codeTtl;
            maxAttempts = maxAttempts <= 0 ? 5 : maxAttempts;
            resendCooldown = resendCooldown == null ? Duration.ofMinutes(1) : resendCooldown;
        }
    }

    public record TwoFactor(String issuer, Duration challengeTtl, int maxAttempts) {
        public TwoFactor {
            issuer = issuer == null || issuer.isBlank() ? "FinanceManage" : issuer;
            challengeTtl = challengeTtl == null ? Duration.ofMinutes(5) : challengeTtl;
            maxAttempts = maxAttempts <= 0 ? 5 : maxAttempts;
        }
    }

    /** OAuth client ids (web, Android, iOS) whose ID tokens are accepted as {@code aud}. */
    public record Google(List<String> clientIds) {
        public Google {
            clientIds = clientIds == null ? List.of() : clientIds.stream().filter(id -> !id.isBlank()).toList();
        }
    }

    public record Mail(String from) {
        public Mail {
            from = from == null || from.isBlank() ? "no-reply@financemanage.local" : from;
        }
    }
}
