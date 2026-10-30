package com.financemanage.identity.application;

import com.financemanage.identity.application.EmailSender.EmailMessage;
import com.financemanage.identity.domain.UserAccount;
import com.financemanage.identity.domain.UserAccountRepository;
import com.financemanage.identity.domain.VerificationCode;
import com.financemanage.identity.domain.VerificationCodeRepository;
import com.financemanage.identity.domain.VerificationPurpose;
import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.NotFoundException;
import com.financemanage.shared.domain.TooManyAttemptsException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Six-digit codes sent by email to verify the address and to reset a forgotten password. A new code replaces the
 * previous one; codes expire, can be guessed only a few times and can be re-sent only after a cooldown.
 */
@Service
@Transactional
public class VerificationService {

    private static final int CODE_DIGITS = 6;

    private final VerificationCodeRepository codes;
    private final UserAccountRepository users;
    private final EmailSender emailSender;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessions;
    private final IdentityProperties.Verification settings;
    private final Clock clock;

    @SuppressWarnings("java:S107")
    public VerificationService(VerificationCodeRepository codes, UserAccountRepository users, EmailSender emailSender,
                               PasswordEncoder passwordEncoder, SessionService sessions,
                               IdentityProperties properties, Clock clock) {
        this.codes = codes;
        this.users = users;
        this.emailSender = emailSender;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
        this.settings = properties.verification();
        this.clock = clock;
    }

    public void sendEmailVerification(UserAccount user) {
        String code = issue(user, VerificationPurpose.EMAIL_VERIFICATION);
        boolean vi = "vi".equals(user.getLocale());
        emailSender.send(new EmailMessage(user.getEmail(),
                vi ? "Mã xác thực email: " + code : "Email verification code: " + code,
                vi ? "Xin chào %s,\n\nMã xác thực email của bạn là %s. Mã có hiệu lực trong %d phút.\n"
                        .formatted(user.getFullName(), code, settings.codeTtl().toMinutes())
                        : "Hello %s,\n\nYour email verification code is %s. It is valid for %d minutes.\n"
                        .formatted(user.getFullName(), code, settings.codeTtl().toMinutes())));
    }

    public void resendEmailVerification(UUID userId) {
        UserAccount user = user(userId);
        if (user.isEmailVerified()) {
            throw new DomainException("EMAIL_ALREADY_VERIFIED", "The email address is already verified");
        }
        if (inCooldown(userId, VerificationPurpose.EMAIL_VERIFICATION)) {
            throw new TooManyAttemptsException("RESEND_TOO_SOON", "Wait a minute before asking for a new code");
        }
        sendEmailVerification(user);
    }

    /** A wrong code is counted (that change is kept although the call fails). */
    @Transactional(noRollbackFor = DomainException.class)
    public void verifyEmail(UUID userId, String code) {
        UserAccount user = user(userId);
        if (user.isEmailVerified()) {
            return;
        }
        if (!redeem(userId, VerificationPurpose.EMAIL_VERIFICATION, code)) {
            throw new DomainException("INVALID_CODE", "The code is wrong or expired");
        }
        user.markEmailVerified(clock.instant());
    }

    /** Always succeeds, so the response does not reveal whether the email is registered. */
    public void requestPasswordReset(String email) {
        Optional<UserAccount> found = users.findByEmail(UserAccount.normalize(email));
        if (found.isEmpty() || inCooldown(found.get().getId(), VerificationPurpose.PASSWORD_RESET)) {
            return;
        }
        UserAccount user = found.get();
        String code = issue(user, VerificationPurpose.PASSWORD_RESET);
        boolean vi = "vi".equals(user.getLocale());
        emailSender.send(new EmailMessage(user.getEmail(),
                vi ? "Mã đặt lại mật khẩu: " + code : "Password reset code: " + code,
                vi ? ("Xin chào %s,\n\nMã đặt lại mật khẩu của bạn là %s, có hiệu lực trong %d phút. "
                        + "Nếu bạn không yêu cầu, hãy bỏ qua email này.\n")
                        .formatted(user.getFullName(), code, settings.codeTtl().toMinutes())
                        : ("Hello %s,\n\nYour password reset code is %s, valid for %d minutes. "
                        + "If you did not ask for it, ignore this email.\n")
                        .formatted(user.getFullName(), code, settings.codeTtl().toMinutes())));
    }

    /**
     * Sets a new password with a code from {@link #requestPasswordReset}. Also verifies the email (the user proved
     * they read it), unlocks the account and signs every device out.
     */
    @Transactional(noRollbackFor = DomainException.class)
    public void resetPassword(String email, String code, String newPassword) {
        UserAccount user = users.findByEmail(UserAccount.normalize(email))
                .filter(u -> redeem(u.getId(), VerificationPurpose.PASSWORD_RESET, code))
                .orElseThrow(() -> new DomainException("INVALID_CODE", "The code is wrong or expired"));
        user.changePasswordHash(passwordEncoder.encode(newPassword));
        user.markEmailVerified(clock.instant());
        user.recordSuccessfulLogin();
        sessions.revokeAll(user.getId(), null);
    }

    private String issue(UserAccount user, VerificationPurpose purpose) {
        Instant now = clock.instant();
        codes.findByUserIdAndPurposeAndUsedAtIsNull(user.getId(), purpose).forEach(c -> c.invalidate(now));
        String code = Hashing.randomDigits(CODE_DIGITS);
        codes.save(new VerificationCode(user.getId(), purpose, hash(user.getId(), purpose, code), now,
                now.plus(settings.codeTtl())));
        return code;
    }

    private boolean redeem(UUID userId, VerificationPurpose purpose, String code) {
        Instant now = clock.instant();
        return codes.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(userId, purpose)
                .map(c -> c.redeem(hash(userId, purpose, code == null ? "" : code.strip()), now,
                        settings.maxAttempts()))
                .orElse(false);
    }

    private boolean inCooldown(UUID userId, VerificationPurpose purpose) {
        Instant now = clock.instant();
        return codes.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(userId, purpose)
                .filter(c -> now.isBefore(c.getCreatedAt().plus(settings.resendCooldown())))
                .isPresent();
    }

    /** Salted with the user and the purpose, so the same digits never hash alike across users. */
    private static String hash(UUID userId, VerificationPurpose purpose, String code) {
        return Hashing.sha256(userId + ":" + purpose + ":" + code);
    }

    private UserAccount user(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("User", userId));
    }
}
