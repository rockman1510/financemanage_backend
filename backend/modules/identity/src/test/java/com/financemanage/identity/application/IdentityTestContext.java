package com.financemanage.identity.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.financemanage.identity.application.EmailSender.EmailMessage;
import com.financemanage.identity.domain.LoginChallenge;
import com.financemanage.identity.domain.LoginChallengeRepository;
import com.financemanage.identity.domain.RecoveryCode;
import com.financemanage.identity.domain.RecoveryCodeRepository;
import com.financemanage.identity.domain.RefreshToken;
import com.financemanage.identity.domain.RefreshTokenRepository;
import com.financemanage.identity.domain.UserAccount;
import com.financemanage.identity.domain.UserAccountRepository;
import com.financemanage.identity.domain.VerificationCode;
import com.financemanage.identity.domain.VerificationCodeRepository;
import com.financemanage.identity.domain.VerificationPurpose;
import com.financemanage.shared.domain.DomainException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Wires the real identity services to in-memory repositories (Mockito answers backed by maps), a controllable clock,
 * a recording mailbox and fake adapters – keeps the tests fast and free of a database.
 */
final class IdentityTestContext {

    final Map<UUID, UserAccount> users = new LinkedHashMap<>();
    final Map<UUID, RefreshToken> tokens = new LinkedHashMap<>();
    final Map<UUID, VerificationCode> codes = new LinkedHashMap<>();
    final Map<UUID, RecoveryCode> recoveryCodes = new LinkedHashMap<>();
    final Map<UUID, LoginChallenge> challenges = new LinkedHashMap<>();
    final List<Object> events = new ArrayList<>();
    final List<EmailMessage> mails = new ArrayList<>();
    final MutableClock clock = new MutableClock(Instant.parse("2025-01-01T00:00:00Z"));

    final UserAccountRepository userRepository = mock(UserAccountRepository.class);
    final RefreshTokenRepository tokenRepository = mock(RefreshTokenRepository.class);
    final VerificationCodeRepository codeRepository = mock(VerificationCodeRepository.class);
    final RecoveryCodeRepository recoveryRepository = mock(RecoveryCodeRepository.class);
    final LoginChallengeRepository challengeRepository = mock(LoginChallengeRepository.class);

    /** Reversible fake encoder keeps the tests fast and deterministic. */
    final PasswordEncoder encoder = new PasswordEncoder() {
        @Override
        public String encode(CharSequence raw) {
            return "hashed:" + raw;
        }

        @Override
        public boolean matches(CharSequence raw, String encoded) {
            return encoded.equals("hashed:" + raw);
        }
    };

    final TokenIssuer issuer = new TokenIssuer() {
        @Override
        public String issueAccessToken(UserAccount user, UUID sessionId) {
            return "access-" + user.getEmail() + "-" + sessionId;
        }

        @Override
        public long accessTokenTtlSeconds() {
            return 900;
        }
    };

    final SecretCipher cipher = new SecretCipher() {
        @Override
        public String encrypt(String plainText) {
            return "enc:" + plainText;
        }

        @Override
        public String decrypt(String cipherText) {
            return cipherText.substring(4);
        }
    };

    /** Fake Google: the token "google|subject|email|name" is valid, anything else is not. */
    final GoogleTokenVerifier google = idToken -> {
        String[] parts = idToken.split("\\|");
        if (parts.length != 4 || !parts[0].equals("google")) {
            throw new BadCredentialsException("Invalid Google ID token");
        }
        return new GoogleTokenVerifier.GoogleIdentity(parts[1], parts[2], parts[3]);
    };

    final IdentityProperties properties = new IdentityProperties(null, null, null, null, null);
    final SessionService sessions = new SessionService(tokenRepository, userRepository, issuer, clock,
            Duration.ofDays(30));
    final VerificationService verification = new VerificationService(codeRepository, userRepository, mails::add,
            encoder, sessions, properties, clock);
    final TwoFactorService twoFactor = new TwoFactorService(userRepository, recoveryRepository, cipher, properties,
            clock);
    final AuthService auth = new AuthService(userRepository, challengeRepository, encoder, sessions, verification,
            twoFactor, google, events::add, properties, clock);
    final AccountService accounts = new AccountService(userRepository, recoveryRepository, encoder, sessions,
            events::add);

    IdentityTestContext() {
        when(userRepository.save(any())).thenAnswer(inv -> put(users, inv.<UserAccount>getArgument(0).getId(),
                inv.getArgument(0)));
        when(userRepository.findById(any()))
                .thenAnswer(inv -> Optional.ofNullable(users.get(inv.<UUID>getArgument(0))));
        when(userRepository.findByEmail(anyString())).thenAnswer(inv -> users.values().stream()
                .filter(u -> u.getEmail().equals(inv.getArgument(0))).findFirst());
        when(userRepository.existsByEmail(anyString())).thenAnswer(inv -> users.values().stream()
                .anyMatch(u -> u.getEmail().equals(inv.getArgument(0))));
        when(userRepository.findByGoogleSubject(anyString())).thenAnswer(inv -> users.values().stream()
                .filter(u -> inv.getArgument(0).equals(u.getGoogleSubject())).findFirst());
        doAnswer(inv -> users.remove(inv.<UserAccount>getArgument(0).getId())).when(userRepository).delete(any());

        when(tokenRepository.save(any())).thenAnswer(inv -> put(tokens, inv.<RefreshToken>getArgument(0).getId(),
                inv.getArgument(0)));
        when(tokenRepository.findByTokenHash(anyString())).thenAnswer(inv -> tokens.values().stream()
                .filter(t -> t.getTokenHash().equals(inv.getArgument(0))).findFirst());
        when(tokenRepository.findByUserIdAndRevokedFalseAndExpiresAtAfterOrderByCreatedAtDesc(any(), any()))
                .thenAnswer(inv -> tokens.values().stream()
                        .filter(t -> t.getUserId().equals(inv.getArgument(0)) && t.isUsable(inv.getArgument(1)))
                        .toList());
        when(tokenRepository.findBySessionIdAndRevokedFalse(any())).thenAnswer(inv -> tokens.values().stream()
                .filter(t -> t.getSessionId().equals(inv.getArgument(0)) && !t.isRevoked()).toList());
        when(tokenRepository.findByUserIdAndRevokedFalse(any())).thenAnswer(inv -> tokens.values().stream()
                .filter(t -> t.getUserId().equals(inv.getArgument(0)) && !t.isRevoked()).toList());

        when(codeRepository.save(any())).thenAnswer(inv -> put(codes, inv.<VerificationCode>getArgument(0).getId(),
                inv.getArgument(0)));
        when(codeRepository.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(any(), any()))
                .thenAnswer(inv -> codesOf(inv.getArgument(0), inv.getArgument(1)).stream()
                        .max(Comparator.comparing(VerificationCode::getCreatedAt)));
        when(codeRepository.findByUserIdAndPurposeAndUsedAtIsNull(any(), any()))
                .thenAnswer(inv -> codesOf(inv.getArgument(0), inv.getArgument(1)).stream()
                        .filter(c -> c.getUsedAt() == null).toList());

        when(recoveryRepository.save(any())).thenAnswer(inv -> put(recoveryCodes,
                inv.<RecoveryCode>getArgument(0).getId(), inv.getArgument(0)));
        when(recoveryRepository.findByUserIdAndCodeHashAndUsedAtIsNull(any(), anyString()))
                .thenAnswer(inv -> recoveryCodes.values().stream()
                        .filter(c -> c.getUserId().equals(inv.getArgument(0))
                                && c.getCodeHash().equals(inv.getArgument(1)) && c.getUsedAt() == null)
                        .findFirst());
        when(recoveryRepository.findByUserIdAndUsedAtIsNull(any())).thenAnswer(inv -> recoveryCodes.values()
                .stream().filter(c -> c.getUserId().equals(inv.getArgument(0)) && c.getUsedAt() == null).toList());
        doAnswer(inv -> recoveryCodes.values().removeIf(c -> c.getUserId().equals(inv.getArgument(0))))
                .when(recoveryRepository).deleteByUserId(any());

        when(challengeRepository.save(any())).thenAnswer(inv -> put(challenges,
                inv.<LoginChallenge>getArgument(0).getId(), inv.getArgument(0)));
        when(challengeRepository.findByTokenHash(anyString())).thenAnswer(inv -> challenges.values().stream()
                .filter(c -> c.getTokenHash().equals(inv.getArgument(0))).findFirst());
    }

    UserAccount user(String email) {
        return users.values().stream().filter(u -> u.getEmail().equals(email)).findFirst().orElseThrow();
    }

    /** The six-digit code of the latest email sent to {@code email}. */
    String lastCodeSentTo(String email) {
        EmailMessage mail = mails.reversed().stream().filter(m -> m.to().equals(email)).findFirst().orElseThrow();
        Matcher matcher = Pattern.compile("\\b(\\d{6})\\b").matcher(mail.subject());
        if (!matcher.find()) {
            throw new DomainException("NO_CODE", "No code in " + mail.subject());
        }
        return matcher.group(1);
    }

    long activeSessions(UUID userId) {
        return tokens.values().stream().filter(t -> t.getUserId().equals(userId) && !t.isRevoked())
                .map(RefreshToken::getSessionId).distinct().count();
    }

    private List<VerificationCode> codesOf(UUID userId, VerificationPurpose purpose) {
        return codes.values().stream()
                .filter(c -> Objects.equals(c.getUserId(), userId) && c.getPurpose() == purpose).toList();
    }

    private static <T> T put(Map<UUID, T> map, UUID id, T value) {
        map.put(id, value);
        return value;
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
