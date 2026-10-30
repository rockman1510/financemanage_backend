package com.financemanage.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.financemanage.identity.api.IdentityEvents.UserRegistered;
import com.financemanage.identity.domain.Totp;
import com.financemanage.identity.domain.UserAccount;
import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.TooManyAttemptsException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

class AuthServiceTest {

    private final IdentityTestContext ctx = new IdentityTestContext();
    private final AuthService auth = ctx.auth;

    @Test
    void registerNormalizesEmailSendsACodeAndSignsIn() {
        TokenPair tokens = auth.register("  An@Example.com ", "secret123", "Nguyễn An", "Pixel 8");

        UserAccount user = ctx.user("an@example.com");
        assertThat(tokens.accessToken()).startsWith("access-an@example.com");
        assertThat(tokens.expiresInSeconds()).isEqualTo(900);
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(ctx.events).singleElement().isInstanceOfSatisfying(UserRegistered.class,
                e -> assertThat(e.email()).isEqualTo("an@example.com"));
        assertThat(ctx.mails).singleElement().satisfies(m -> assertThat(m.subject()).startsWith("Mã xác thực email"));
        assertThat(ctx.tokens.values()).singleElement()
                .satisfies(t -> assertThat(t.getDeviceName()).isEqualTo("Pixel 8"));
    }

    @Test
    void registerRejectsDuplicateEmail() {
        auth.register("an@example.com", "secret123", "An", null);

        assertThatThrownBy(() -> auth.register("AN@example.com", "secret123", "An", null))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("EMAIL_TAKEN");
    }

    @Test
    void accountIsLockedAfterFiveWrongPasswords() {
        auth.register("an@example.com", "secret123", "An", null);
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> auth.login("an@example.com", "wrong", null))
                    .isInstanceOf(BadCredentialsException.class);
        }

        assertThatThrownBy(() -> auth.login("an@example.com", "secret123", null))
                .isInstanceOf(TooManyAttemptsException.class).extracting("code").isEqualTo("ACCOUNT_LOCKED");
        ctx.clock.advance(Duration.ofMinutes(16));
        assertThat(auth.login("an@example.com", "secret123", null).twoFactorRequired()).isFalse();
    }

    @Test
    void unknownEmailAndWrongPasswordLookTheSame() {
        auth.register("an@example.com", "secret123", "An", null);

        assertThatThrownBy(() -> auth.login("nobody@example.com", "secret123", null))
                .isInstanceOf(BadCredentialsException.class).hasMessage("Invalid email or password");
        assertThatThrownBy(() -> auth.login("an@example.com", "wrong", null))
                .isInstanceOf(BadCredentialsException.class).hasMessage("Invalid email or password");
    }

    @Test
    void twoFactorSignInNeedsACodeAfterThePassword() {
        auth.register("an@example.com", "secret123", "An", null);
        UserAccount user = ctx.user("an@example.com");
        String secret = ctx.twoFactor.setup(user.getId()).secret();
        List<String> recovery = ctx.twoFactor.enable(user.getId(), Totp.code(secret, Totp.step(ctx.clock.instant())));
        ctx.clock.advance(Duration.ofSeconds(30));

        LoginResult first = auth.login("an@example.com", "secret123", "iPhone");
        assertThat(first.twoFactorRequired()).isTrue();
        assertThat(first.tokens()).isNull();

        assertThatThrownBy(() -> auth.completeTwoFactor(first.challengeToken(), "000000"))
                .isInstanceOf(BadCredentialsException.class);
        TokenPair tokens = auth.completeTwoFactor(first.challengeToken(),
                Totp.code(secret, Totp.step(ctx.clock.instant())));
        assertThat(tokens.accessToken()).startsWith("access-an@example.com");
        assertThatThrownBy(() -> auth.completeTwoFactor(first.challengeToken(), recovery.get(0)))
                .isInstanceOf(BadCredentialsException.class); // the challenge was used

        LoginResult second = auth.login("an@example.com", "secret123", null);
        assertThat(auth.completeTwoFactor(second.challengeToken(), recovery.get(0).toLowerCase())).isNotNull();
        LoginResult third = auth.login("an@example.com", "secret123", null);
        assertThatThrownBy(() -> auth.completeTwoFactor(third.challengeToken(), recovery.get(0)))
                .isInstanceOf(BadCredentialsException.class); // a recovery code works once
    }

    @Test
    void googleSignInCreatesThenFindsTheAccount() {
        LoginResult created = auth.loginWithGoogle("google|g-1|binh@gmail.com|Bình", "Pixel");
        LoginResult again = auth.loginWithGoogle("google|g-1|binh@gmail.com|Bình", "Pixel");

        UserAccount user = ctx.user("binh@gmail.com");
        assertThat(created.tokens()).isNotNull();
        assertThat(again.tokens()).isNotNull();
        assertThat(ctx.users).hasSize(1);
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.hasPassword()).isFalse();
        assertThat(ctx.events).singleElement().isInstanceOf(UserRegistered.class);
        assertThatThrownBy(() -> auth.loginWithGoogle("forged", null)).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void googleSignInTakesOverAnUnverifiedAccountWithTheSameEmail() {
        auth.register("binh@gmail.com", "attacker-pass", "Kẻ gian", null);

        auth.loginWithGoogle("google|g-2|binh@gmail.com|Bình", null);

        UserAccount user = ctx.user("binh@gmail.com");
        assertThat(user.getGoogleSubject()).isEqualTo("g-2");
        assertThat(user.hasPassword()).isFalse(); // whoever registered the address first loses access
        assertThat(ctx.activeSessions(user.getId())).isEqualTo(1);
    }

    @Test
    void googleSignInLinksAVerifiedAccountAndKeepsItsPassword() {
        auth.register("an@example.com", "secret123", "An", null);
        UserAccount user = ctx.user("an@example.com");
        ctx.verification.verifyEmail(user.getId(), ctx.lastCodeSentTo("an@example.com"));

        auth.loginWithGoogle("google|g-3|an@example.com|An", null);

        assertThat(user.getGoogleSubject()).isEqualTo("g-3");
        assertThat(auth.login("an@example.com", "secret123", null).tokens()).isNotNull();
    }
}
