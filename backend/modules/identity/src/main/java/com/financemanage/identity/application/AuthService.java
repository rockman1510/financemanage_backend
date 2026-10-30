package com.financemanage.identity.application;

import com.financemanage.identity.api.IdentityEvents.UserRegistered;
import com.financemanage.identity.application.GoogleTokenVerifier.GoogleIdentity;
import com.financemanage.identity.domain.LoginChallenge;
import com.financemanage.identity.domain.LoginChallengeRepository;
import com.financemanage.identity.domain.UserAccount;
import com.financemanage.identity.domain.UserAccountRepository;
import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.TooManyAttemptsException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sign-up and sign-in. Wrong passwords and wrong two-factor codes count towards a temporary account lock; those
 * counters are kept even though the call fails ({@code noRollbackFor}).
 */
@Service
@Transactional
public class AuthService {

    private final UserAccountRepository users;
    private final LoginChallengeRepository challenges;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessions;
    private final VerificationService verification;
    private final TwoFactorService twoFactor;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final ApplicationEventPublisher events;
    private final IdentityProperties properties;
    private final Clock clock;

    @SuppressWarnings("java:S107")
    public AuthService(UserAccountRepository users, LoginChallengeRepository challenges,
                       PasswordEncoder passwordEncoder, SessionService sessions, VerificationService verification,
                       TwoFactorService twoFactor, GoogleTokenVerifier googleTokenVerifier,
                       ApplicationEventPublisher events, IdentityProperties properties, Clock clock) {
        this.users = users;
        this.challenges = challenges;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
        this.verification = verification;
        this.twoFactor = twoFactor;
        this.googleTokenVerifier = googleTokenVerifier;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
    }

    /** Creates the account, emails a verification code and signs the user in. */
    public TokenPair register(String email, String password, String fullName, String deviceName) {
        if (users.existsByEmail(UserAccount.normalize(email))) {
            throw new DomainException("EMAIL_TAKEN", "Email is already registered");
        }
        UserAccount user = users.save(new UserAccount(email, passwordEncoder.encode(password), fullName));
        events.publishEvent(new UserRegistered(user.getId(), user.getEmail(), user.getFullName()));
        verification.sendEmailVerification(user);
        return sessions.start(user, deviceName);
    }

    @Transactional(noRollbackFor = BadCredentialsException.class)
    public LoginResult login(String email, String password, String deviceName) {
        UserAccount user = users.findByEmail(UserAccount.normalize(email))
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        Instant now = clock.instant();
        requireNotLocked(user, now);
        if (!user.hasPassword() || !passwordEncoder.matches(password, user.getPasswordHash())) {
            recordFailure(user, now);
            throw new BadCredentialsException("Invalid email or password");
        }
        return signIn(user, deviceName);
    }

    /**
     * Signs in with a Google ID token. Finds the account by Google id, else by email (Google verified it) and links
     * it, else creates one. Linking an account whose email was never verified drops its password and sessions, so
     * whoever registered the address before its owner cannot keep access.
     */
    public LoginResult loginWithGoogle(String idToken, String deviceName) {
        GoogleIdentity google = googleTokenVerifier.verify(idToken);
        Optional<UserAccount> bySubject = users.findByGoogleSubject(google.subject());
        UserAccount user;
        if (bySubject.isPresent()) {
            user = bySubject.get();
        } else {
            Optional<UserAccount> byEmail = users.findByEmail(UserAccount.normalize(google.email()));
            if (byEmail.isPresent()) {
                user = byEmail.get();
                if (!user.isEmailVerified()) {
                    user.changePasswordHash(null);
                    sessions.revokeAll(user.getId(), null);
                }
                user.linkGoogle(google.subject());
                user.markEmailVerified(clock.instant());
            } else {
                user = users.save(UserAccount.fromGoogle(google.email(), google.name(), google.subject(),
                        clock.instant()));
                events.publishEvent(new UserRegistered(user.getId(), user.getEmail(), user.getFullName()));
            }
        }
        requireNotLocked(user, clock.instant());
        return signIn(user, deviceName);
    }

    /** Second sign-in step: the challenge from {@link #login} plus an authenticator or recovery code. */
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public TokenPair completeTwoFactor(String challengeToken, String code) {
        IdentityProperties.TwoFactor settings = properties.twoFactor();
        Instant now = clock.instant();
        LoginChallenge challenge = challenges.findByTokenHash(Hashing.sha256(challengeToken))
                .filter(c -> c.isUsable(now, settings.maxAttempts()))
                .orElseThrow(() -> new BadCredentialsException("Sign-in expired, enter your password again"));
        UserAccount user = users.findById(challenge.getUserId())
                .orElseThrow(() -> new BadCredentialsException("Sign-in expired, enter your password again"));
        requireNotLocked(user, now);
        if (!twoFactor.verify(user, code)) {
            challenge.recordFailure();
            recordFailure(user, now);
            throw new BadCredentialsException("Invalid two-factor code");
        }
        challenge.complete();
        user.recordSuccessfulLogin();
        return sessions.start(user, challenge.getDeviceName());
    }

    public TokenPair refresh(String refreshToken) {
        return sessions.refresh(refreshToken);
    }

    public void logout(String refreshToken) {
        sessions.logout(refreshToken);
    }

    private LoginResult signIn(UserAccount user, String deviceName) {
        if (user.isTotpEnabled()) {
            String token = Hashing.randomToken();
            IdentityProperties.TwoFactor settings = properties.twoFactor();
            challenges.save(new LoginChallenge(user.getId(), Hashing.sha256(token), deviceName,
                    clock.instant().plus(settings.challengeTtl())));
            return LoginResult.challenge(token, settings.challengeTtl().toSeconds());
        }
        user.recordSuccessfulLogin();
        return LoginResult.signedIn(sessions.start(user, deviceName));
    }

    private void recordFailure(UserAccount user, Instant now) {
        IdentityProperties.Login settings = properties.login();
        user.recordFailedLogin(now, settings.maxAttempts(), settings.lockDuration());
        users.save(user);
    }

    private static void requireNotLocked(UserAccount user, Instant now) {
        if (user.isLocked(now)) {
            throw new TooManyAttemptsException("ACCOUNT_LOCKED",
                    "Too many failed sign-in attempts; try again later or reset the password");
        }
    }
}
