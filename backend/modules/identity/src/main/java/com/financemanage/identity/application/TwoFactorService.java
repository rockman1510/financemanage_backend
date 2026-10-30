package com.financemanage.identity.application;

import com.financemanage.identity.domain.RecoveryCode;
import com.financemanage.identity.domain.RecoveryCodeRepository;
import com.financemanage.identity.domain.Totp;
import com.financemanage.identity.domain.UserAccount;
import com.financemanage.identity.domain.UserAccountRepository;
import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.NotFoundException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Two-factor authentication with an authenticator app (TOTP). Setup stores an encrypted secret; it becomes active
 * once the user confirms a code, and ten one-time recovery codes are returned for when the phone is lost.
 */
@Service
@Transactional
public class TwoFactorService {

    static final int RECOVERY_CODES = 10;
    private static final String RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O, 1/I

    private final UserAccountRepository users;
    private final RecoveryCodeRepository recoveryCodes;
    private final SecretCipher cipher;
    private final IdentityProperties.TwoFactor settings;
    private final Clock clock;

    public TwoFactorService(UserAccountRepository users, RecoveryCodeRepository recoveryCodes, SecretCipher cipher,
                            IdentityProperties properties, Clock clock) {
        this.users = users;
        this.recoveryCodes = recoveryCodes;
        this.cipher = cipher;
        this.settings = properties.twoFactor();
        this.clock = clock;
    }

    /** Creates a new secret (replacing an unconfirmed one); two-factor stays off until {@link #enable}. */
    public TwoFactorSetup setup(UUID userId) {
        UserAccount user = user(userId);
        String secret = Totp.newSecret(Hashing.RANDOM);
        user.startTotpSetup(cipher.encrypt(secret));
        return new TwoFactorSetup(secret, Totp.otpauthUri(settings.issuer(), user.getEmail(), secret));
    }

    /** Turns two-factor on with a code from the authenticator; returns the recovery codes (shown only once). */
    public List<String> enable(UUID userId, String code) {
        UserAccount user = user(userId);
        if (user.isTotpEnabled()) {
            throw new DomainException("TWO_FACTOR_ALREADY_ENABLED", "Two-factor authentication is already on");
        }
        if (user.getTotpSecret() == null) {
            throw new DomainException("TWO_FACTOR_NOT_SET_UP", "Start the two-factor setup first");
        }
        OptionalLong step = Totp.verify(cipher.decrypt(user.getTotpSecret()), code, clock.instant(), null);
        if (step.isEmpty()) {
            throw new DomainException("INVALID_CODE", "The code is wrong or expired");
        }
        user.enableTotp(step.getAsLong());
        recoveryCodes.deleteByUserId(userId);
        List<String> plain = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODES; i++) {
            String recovery = randomRecoveryCode();
            plain.add(recovery);
            recoveryCodes.save(new RecoveryCode(userId, hashRecovery(userId, recovery)));
        }
        return plain;
    }

    /** Turns two-factor off; needs a current code (or a recovery code). */
    public void disable(UUID userId, String code) {
        UserAccount user = user(userId);
        if (!user.isTotpEnabled()) {
            throw new DomainException("TWO_FACTOR_NOT_ENABLED", "Two-factor authentication is off");
        }
        if (!verify(user, code)) {
            throw new DomainException("INVALID_CODE", "The code is wrong or expired");
        }
        user.disableTotp();
        recoveryCodes.deleteByUserId(userId);
    }

    /**
     * Checks a sign-in code: a TOTP code (accepted once) or an unused recovery code (used up). The caller decides
     * what a failure means.
     */
    public boolean verify(UserAccount user, String code) {
        if (!user.isTotpEnabled() || code == null) {
            return false;
        }
        String trimmed = code.strip();
        OptionalLong step = Totp.verify(cipher.decrypt(user.getTotpSecret()), trimmed, clock.instant(),
                user.getTotpLastStep());
        if (step.isPresent()) {
            user.acceptTotpStep(step.getAsLong());
            return true;
        }
        return recoveryCodes.findByUserIdAndCodeHashAndUsedAtIsNull(user.getId(), hashRecovery(user.getId(), trimmed))
                .map(recovery -> {
                    recovery.use(clock.instant());
                    return true;
                })
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public int remainingRecoveryCodes(UUID userId) {
        return recoveryCodes.findByUserIdAndUsedAtIsNull(userId).size();
    }

    /** E.g. "K7PQX-3MZRA". */
    private static String randomRecoveryCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            if (i == 5) {
                code.append('-');
            }
            code.append(RECOVERY_ALPHABET.charAt(Hashing.RANDOM.nextInt(RECOVERY_ALPHABET.length())));
        }
        return code.toString();
    }

    /** Case, spaces and the dash do not matter when typing a recovery code. */
    private static String hashRecovery(UUID userId, String code) {
        String normalized = code.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
        return Hashing.sha256(userId + ":recovery:" + normalized);
    }

    private UserAccount user(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("User", userId));
    }
}
