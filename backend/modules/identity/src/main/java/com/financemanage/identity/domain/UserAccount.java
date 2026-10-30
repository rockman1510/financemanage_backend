package com.financemanage.identity.domain;

import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

/** Identity is a supporting subdomain, so the JPA entity doubles as the domain model. */
@Entity
@Table(name = "user_account", schema = "identity")
public class UserAccount {

    public static final Set<String> LOCALES = Set.of("vi", "en");
    private static final int MAX_NAME_LENGTH = 100;

    @Id
    private UUID id;
    @Column(nullable = false, unique = true)
    private String email;
    /** {@code null} for accounts created with Google sign-in until the user sets a password. */
    @Column(name = "password_hash")
    private String passwordHash;
    @Column(name = "full_name", nullable = false)
    private String fullName;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;
    @Column(name = "google_subject", unique = true)
    private String googleSubject;
    @Column(name = "default_currency", nullable = false)
    private String defaultCurrency = "VND";
    @Column(nullable = false)
    private String locale = "vi";
    @Column(name = "time_zone", nullable = false)
    private String timeZone = "Asia/Ho_Chi_Minh";
    /** Day a "month" starts for budgets and reports, e.g. 25 when the salary arrives on the 25th. */
    @Column(name = "month_start_day", nullable = false)
    private int monthStartDay = 1;
    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;
    @Column(name = "locked_until")
    private Instant lockedUntil;
    /** TOTP secret, encrypted; set while setting up two-factor authentication and kept while it is enabled. */
    @Column(name = "totp_secret")
    private String totpSecret;
    @Column(name = "totp_enabled", nullable = false)
    private boolean totpEnabled;
    /** Last accepted TOTP time step – a code cannot be used twice. */
    @Column(name = "totp_last_step")
    private Long totpLastStep;

    protected UserAccount() {
    }

    public UserAccount(String email, String passwordHash, String fullName) {
        this.id = UUID.randomUUID();
        this.email = normalize(email);
        this.passwordHash = passwordHash;
        this.fullName = fullName.trim();
        this.createdAt = Instant.now();
    }

    /** Account created by Google sign-in: Google already verified the email, there is no password. */
    public static UserAccount fromGoogle(String email, String fullName, String googleSubject, Instant now) {
        UserAccount user = new UserAccount(email, null, fullName == null || fullName.isBlank() ? email : fullName);
        user.googleSubject = googleSubject;
        user.emailVerifiedAt = now;
        return user;
    }

    public static String normalize(String email) {
        return email.trim().toLowerCase();
    }

    public boolean hasPassword() {
        return passwordHash != null;
    }

    public void changePasswordHash(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /** Counts a wrong password; the account is locked for {@code lockDuration} after {@code maxAttempts}. */
    public void recordFailedLogin(Instant now, int maxAttempts, Duration lockDuration) {
        failedLoginCount++;
        if (failedLoginCount >= maxAttempts) {
            lockedUntil = now.plus(lockDuration);
            failedLoginCount = 0;
        }
    }

    public void recordSuccessfulLogin() {
        failedLoginCount = 0;
        lockedUntil = null;
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    public void markEmailVerified(Instant now) {
        if (emailVerifiedAt == null) {
            emailVerifiedAt = now;
        }
    }

    public void linkGoogle(String subject) {
        this.googleSubject = subject;
    }

    /** Partial update: {@code null} values stay unchanged. */
    public void updateProfile(String newFullName, String newCurrency, String newLocale, String newTimeZone,
                              Integer newMonthStartDay) {
        if (newFullName != null) {
            String name = newFullName.trim();
            if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
                throw new DomainException("INVALID_NAME", "Full name is required and at most 100 characters");
            }
            fullName = name;
        }
        if (newCurrency != null) {
            defaultCurrency = Money.currency(newCurrency).getCurrencyCode();
        }
        if (newLocale != null) {
            if (!LOCALES.contains(newLocale)) {
                throw new DomainException("INVALID_LOCALE", "Supported languages: " + LOCALES);
            }
            locale = newLocale;
        }
        if (newTimeZone != null) {
            try {
                timeZone = ZoneId.of(newTimeZone).getId();
            } catch (DateTimeException e) {
                throw new DomainException("INVALID_TIME_ZONE", "Unknown time zone " + newTimeZone);
            }
        }
        if (newMonthStartDay != null) {
            if (newMonthStartDay < 1 || newMonthStartDay > 28) {
                throw new DomainException("INVALID_MONTH_START_DAY", "Month start day must be between 1 and 28");
            }
            monthStartDay = newMonthStartDay;
        }
    }

    /** Stores a new (encrypted) secret that becomes active once {@link #enableTotp(long)} confirms a code. */
    public void startTotpSetup(String encryptedSecret) {
        if (totpEnabled) {
            throw new DomainException("TWO_FACTOR_ALREADY_ENABLED", "Two-factor authentication is already on");
        }
        totpSecret = encryptedSecret;
        totpLastStep = null;
    }

    public void enableTotp(long confirmedStep) {
        if (totpSecret == null) {
            throw new DomainException("TWO_FACTOR_NOT_SET_UP", "Start the two-factor setup first");
        }
        totpEnabled = true;
        totpLastStep = confirmedStep;
    }

    public void disableTotp() {
        totpEnabled = false;
        totpSecret = null;
        totpLastStep = null;
    }

    public void acceptTotpStep(long step) {
        totpLastStep = step;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getEmailVerifiedAt() { return emailVerifiedAt; }
    public String getGoogleSubject() { return googleSubject; }
    public String getDefaultCurrency() { return defaultCurrency; }
    public String getLocale() { return locale; }
    public String getTimeZone() { return timeZone; }
    public int getMonthStartDay() { return monthStartDay; }
    public String getTotpSecret() { return totpSecret; }
    public boolean isTotpEnabled() { return totpEnabled; }
    public Long getTotpLastStep() { return totpLastStep; }
}
