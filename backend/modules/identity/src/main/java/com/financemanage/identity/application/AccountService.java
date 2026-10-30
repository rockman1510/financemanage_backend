package com.financemanage.identity.application;

import com.financemanage.identity.api.IdentityEvents.UserDeleted;
import com.financemanage.identity.api.IdentityEvents.UserProfileChanged;
import com.financemanage.identity.domain.RecoveryCodeRepository;
import com.financemanage.identity.domain.UserAccount;
import com.financemanage.identity.domain.UserAccountRepository;
import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.NotFoundException;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The signed-in user's own account: profile, password and deletion. */
@Service
@Transactional
public class AccountService {

    private final UserAccountRepository users;
    private final RecoveryCodeRepository recoveryCodes;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessions;
    private final ApplicationEventPublisher events;

    public AccountService(UserAccountRepository users, RecoveryCodeRepository recoveryCodes,
                          PasswordEncoder passwordEncoder, SessionService sessions,
                          ApplicationEventPublisher events) {
        this.users = users;
        this.recoveryCodes = recoveryCodes;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public UserAccount profile(UUID userId) {
        return user(userId);
    }

    public UserAccount updateProfile(UUID userId, String fullName, String defaultCurrency, String locale,
                                     String timeZone, Integer monthStartDay) {
        UserAccount user = user(userId);
        user.updateProfile(fullName, defaultCurrency, locale, timeZone, monthStartDay);
        events.publishEvent(new UserProfileChanged(userId, user.getDefaultCurrency(), user.getLocale(),
                user.getTimeZone(), user.getMonthStartDay()));
        return user;
    }

    /**
     * Changes (or, for a Google-only account, sets) the password and signs every other device out. The current
     * password is required when the account has one.
     */
    public void changePassword(UUID userId, UUID currentSessionId, String currentPassword, String newPassword) {
        UserAccount user = user(userId);
        requirePassword(user, currentPassword);
        user.changePasswordHash(passwordEncoder.encode(newPassword));
        sessions.revokeAll(userId, currentSessionId);
    }

    /**
     * Deletes the account. {@code confirmEmail} must repeat the account email, and the password is required when the
     * account has one. Other modules delete their data when they receive {@link UserDeleted}.
     */
    public void delete(UUID userId, String password, String confirmEmail) {
        UserAccount user = user(userId);
        if (confirmEmail == null || !user.getEmail().equals(UserAccount.normalize(confirmEmail))) {
            throw new DomainException("CONFIRMATION_MISMATCH", "Type the account email to confirm the deletion");
        }
        requirePassword(user, password);
        recoveryCodes.deleteByUserId(userId);
        users.delete(user); // refresh tokens, codes and challenges go with it (ON DELETE CASCADE)
        events.publishEvent(new UserDeleted(userId));
    }

    private void requirePassword(UserAccount user, String password) {
        if (user.hasPassword() && (password == null || !passwordEncoder.matches(password, user.getPasswordHash()))) {
            throw new DomainException("WRONG_PASSWORD", "The current password is wrong");
        }
    }

    private UserAccount user(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("User", userId));
    }
}
