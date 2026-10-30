package com.financemanage.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.financemanage.shared.domain.DomainException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserAccountTest {

    private static final Instant NOW = Instant.parse("2025-01-01T00:00:00Z");

    @Test
    void locksAfterTooManyWrongPasswordsAndUnlocksLater() {
        UserAccount user = new UserAccount("an@example.com", "hash", "An");
        for (int i = 0; i < 4; i++) {
            user.recordFailedLogin(NOW, 5, Duration.ofMinutes(15));
        }
        assertThat(user.isLocked(NOW)).isFalse();

        user.recordFailedLogin(NOW, 5, Duration.ofMinutes(15));

        assertThat(user.isLocked(NOW.plusSeconds(60))).isTrue();
        assertThat(user.isLocked(NOW.plus(Duration.ofMinutes(15)))).isFalse();
    }

    @Test
    void validatesProfileChanges() {
        UserAccount user = new UserAccount("an@example.com", "hash", "An");
        user.updateProfile(" An Nguyễn ", "USD", "en", "Europe/Paris", 25);

        assertThat(user.getFullName()).isEqualTo("An Nguyễn");
        assertThat(user.getDefaultCurrency()).isEqualTo("USD");
        assertThat(user.getMonthStartDay()).isEqualTo(25);
        assertThatThrownBy(() -> user.updateProfile(null, "XYZ", null, null, null))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("INVALID_CURRENCY");
        assertThatThrownBy(() -> user.updateProfile(null, null, "fr", null, null))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("INVALID_LOCALE");
        assertThatThrownBy(() -> user.updateProfile(null, null, null, "Mars/Base", null))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("INVALID_TIME_ZONE");
        assertThatThrownBy(() -> user.updateProfile(null, null, null, null, 31))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("INVALID_MONTH_START_DAY");
    }

    @Test
    void googleAccountsHaveAVerifiedEmailAndNoPassword() {
        UserAccount user = UserAccount.fromGoogle("An@Gmail.com", "", "google-1", NOW);

        assertThat(user.getEmail()).isEqualTo("an@gmail.com");
        assertThat(user.getFullName()).isEqualTo("An@Gmail.com");
        assertThat(user.hasPassword()).isFalse();
        assertThat(user.isEmailVerified()).isTrue();
    }

    @Test
    void verificationCodeIsBurntAfterTooManyWrongGuesses() {
        VerificationCode code = new VerificationCode(null, VerificationPurpose.EMAIL_VERIFICATION, "right", NOW,
                NOW.plusSeconds(900));

        assertThat(code.redeem("wrong", NOW, 3)).isFalse();
        assertThat(code.redeem("wrong", NOW, 3)).isFalse();
        assertThat(code.redeem("wrong", NOW, 3)).isFalse();
        assertThat(code.redeem("right", NOW, 3)).isFalse();
        assertThat(code.isUsable(NOW)).isFalse();
    }
}
