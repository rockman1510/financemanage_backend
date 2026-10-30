package com.financemanage.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.financemanage.identity.api.IdentityEvents.UserDeleted;
import com.financemanage.identity.api.IdentityEvents.UserProfileChanged;
import com.financemanage.identity.domain.Totp;
import com.financemanage.identity.domain.UserAccount;
import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.TooManyAttemptsException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

class SessionAndAccountServiceTest {

    private final IdentityTestContext ctx = new IdentityTestContext();
    private UserAccount user;
    private TokenPair phone;

    @BeforeEach
    void register() {
        phone = ctx.auth.register("an@example.com", "secret123", "An", "Điện thoại");
        user = ctx.user("an@example.com");
    }

    @Test
    void refreshRotatesWithinTheSameSessionAndReuseEndsIt() {
        TokenPair rotated = ctx.auth.refresh(phone.refreshToken());

        assertThat(rotated.refreshToken()).isNotEqualTo(phone.refreshToken());
        assertThat(ctx.sessions.list(user.getId(), null)).singleElement()
                .satisfies(s -> assertThat(s.deviceName()).isEqualTo("Điện thoại"));

        assertThatThrownBy(() -> ctx.auth.refresh(phone.refreshToken())) // replay of the old token
                .isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> ctx.auth.refresh(rotated.refreshToken())) // the whole session is gone
                .isInstanceOf(BadCredentialsException.class);
        assertThat(ctx.sessions.list(user.getId(), null)).isEmpty();
    }

    @Test
    void devicesCanBeListedAndSignedOut() {
        ctx.auth.login("an@example.com", "secret123", "Laptop");
        ctx.auth.login("an@example.com", "secret123", "Máy tính bảng");
        List<SessionView> all = ctx.sessions.list(user.getId(), null);
        assertThat(all).hasSize(3);
        UUID laptop = all.stream().filter(s -> "Laptop".equals(s.deviceName())).findFirst().orElseThrow().id();
        UUID phoneSession = all.stream().filter(s -> "Điện thoại".equals(s.deviceName())).findFirst().orElseThrow()
                .id();

        ctx.sessions.revoke(user.getId(), laptop);
        assertThat(ctx.sessions.list(user.getId(), phoneSession)).hasSize(2)
                .anySatisfy(s -> assertThat(s.current()).isTrue());

        ctx.sessions.revokeAll(user.getId(), phoneSession);
        assertThat(ctx.sessions.list(user.getId(), phoneSession)).singleElement()
                .satisfies(s -> assertThat(s.id()).isEqualTo(phoneSession));
    }

    @Test
    void changingThePasswordSignsOtherDevicesOut() {
        ctx.auth.login("an@example.com", "secret123", "Laptop");
        UUID phoneSession = sessionOf("Điện thoại");

        assertThatThrownBy(() -> ctx.accounts.changePassword(user.getId(), phoneSession, "wrong", "new-secret1"))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("WRONG_PASSWORD");
        ctx.accounts.changePassword(user.getId(), phoneSession, "secret123", "new-secret1");

        assertThat(ctx.sessions.list(user.getId(), phoneSession)).singleElement();
        assertThat(ctx.auth.login("an@example.com", "new-secret1", null).tokens()).isNotNull();
    }

    @Test
    void emailIsVerifiedWithTheLatestCodeAndResendHasACooldown() {
        assertThatThrownBy(() -> ctx.verification.resendEmailVerification(user.getId()))
                .isInstanceOf(TooManyAttemptsException.class).extracting("code").isEqualTo("RESEND_TOO_SOON");
        String first = ctx.lastCodeSentTo("an@example.com");
        ctx.clock.advance(Duration.ofMinutes(2));
        ctx.verification.resendEmailVerification(user.getId());
        String second = ctx.lastCodeSentTo("an@example.com");

        if (!first.equals(second)) {
            assertThatThrownBy(() -> ctx.verification.verifyEmail(user.getId(), first))
                    .isInstanceOf(DomainException.class).extracting("code").isEqualTo("INVALID_CODE");
        }
        ctx.verification.verifyEmail(user.getId(), second);
        assertThat(user.isEmailVerified()).isTrue();
        assertThatThrownBy(() -> ctx.verification.resendEmailVerification(user.getId()))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("EMAIL_ALREADY_VERIFIED");
    }

    @Test
    void passwordResetWorksOnceAndSignsEverybodyOut() {
        ctx.verification.requestPasswordReset("AN@example.com");
        ctx.verification.requestPasswordReset("nobody@example.com"); // silently ignored
        String code = ctx.lastCodeSentTo("an@example.com");

        assertThatThrownBy(() -> ctx.verification.resetPassword("an@example.com", "999999".equals(code) ? "000000"
                : "999999", "new-secret1"))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("INVALID_CODE");
        ctx.verification.resetPassword("an@example.com", code, "new-secret1");

        assertThat(ctx.activeSessions(user.getId())).isZero();
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(ctx.auth.login("an@example.com", "new-secret1", null).tokens()).isNotNull();
        assertThatThrownBy(() -> ctx.verification.resetPassword("an@example.com", code, "other-secret"))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("INVALID_CODE");
    }

    @Test
    void profileChangesArePublished() {
        ctx.accounts.updateProfile(user.getId(), null, "USD", "en", null, 25);

        assertThat(ctx.events).last().isInstanceOfSatisfying(UserProfileChanged.class, e -> {
            assertThat(e.defaultCurrency()).isEqualTo("USD");
            assertThat(e.monthStartDay()).isEqualTo(25);
        });
    }

    @Test
    void twoFactorNeedsAConfirmedCodeAndCanBeTurnedOff() {
        TwoFactorSetup setup = ctx.twoFactor.setup(user.getId());
        assertThat(setup.otpauthUri()).contains("secret=" + setup.secret()).contains("an%40example.com");
        assertThat(user.isTotpEnabled()).isFalse();
        assertThatThrownBy(() -> ctx.twoFactor.enable(user.getId(), "000000"))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("INVALID_CODE");

        List<String> recovery = ctx.twoFactor.enable(user.getId(),
                Totp.code(setup.secret(), Totp.step(ctx.clock.instant())));
        assertThat(recovery).hasSize(TwoFactorService.RECOVERY_CODES)
                .allMatch(c -> c.matches("[A-Z2-9]{5}-[A-Z2-9]{5}"));
        assertThat(user.getTotpSecret()).isEqualTo("enc:" + setup.secret());

        ctx.twoFactor.disable(user.getId(), recovery.get(3));
        assertThat(user.isTotpEnabled()).isFalse();
        assertThat(ctx.recoveryCodes).isEmpty();
    }

    @Test
    void deletingTheAccountNeedsTheEmailAndPassword() {
        assertThatThrownBy(() -> ctx.accounts.delete(user.getId(), "secret123", "other@example.com"))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("CONFIRMATION_MISMATCH");
        assertThatThrownBy(() -> ctx.accounts.delete(user.getId(), "wrong", "an@example.com"))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("WRONG_PASSWORD");

        ctx.accounts.delete(user.getId(), "secret123", "AN@example.com");

        assertThat(ctx.users).isEmpty();
        assertThat(ctx.events).last().isEqualTo(new UserDeleted(user.getId()));
    }

    private UUID sessionOf(String deviceName) {
        return ctx.sessions.list(user.getId(), null).stream().filter(s -> deviceName.equals(s.deviceName()))
                .findFirst().orElseThrow().id();
    }
}
