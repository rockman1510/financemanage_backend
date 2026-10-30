package com.financemanage.identity.infrastructure.web;

import com.financemanage.identity.application.AccountService;
import com.financemanage.identity.application.SessionService;
import com.financemanage.identity.application.SessionView;
import com.financemanage.identity.application.TwoFactorService;
import com.financemanage.identity.application.TwoFactorSetup;
import com.financemanage.identity.application.VerificationService;
import com.financemanage.identity.domain.UserAccount;
import com.financemanage.shared.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user's account: profile, email verification, password, devices, two-factor and deletion. */
@RestController
@RequestMapping("/api/v1/me")
class AccountController {

    private final AccountService accounts;
    private final VerificationService verification;
    private final SessionService sessions;
    private final TwoFactorService twoFactor;

    AccountController(AccountService accounts, VerificationService verification, SessionService sessions,
                      TwoFactorService twoFactor) {
        this.accounts = accounts;
        this.verification = verification;
        this.sessions = sessions;
        this.twoFactor = twoFactor;
    }

    record ProfileResponse(UUID id, String email, String fullName, boolean emailVerified, boolean hasPassword,
                           boolean googleLinked, boolean twoFactorEnabled, String defaultCurrency, String locale,
                           String timeZone, int monthStartDay, Instant createdAt) {

        static ProfileResponse from(UserAccount u) {
            return new ProfileResponse(u.getId(), u.getEmail(), u.getFullName(), u.isEmailVerified(),
                    u.hasPassword(), u.getGoogleSubject() != null, u.isTotpEnabled(), u.getDefaultCurrency(),
                    u.getLocale(), u.getTimeZone(), u.getMonthStartDay(), u.getCreatedAt());
        }
    }

    /** Omitted fields stay unchanged. {@code monthStartDay}: the day budgets and reports start a month. */
    record UpdateProfileRequest(@Size(max = 100) String fullName, @Pattern(regexp = "[A-Z]{3}") String defaultCurrency,
                                @Size(max = 10) String locale, @Size(max = 40) String timeZone,
                                @Min(1) @Max(28) Integer monthStartDay) {
    }

    /** {@code currentPassword} may be omitted only by Google accounts that have no password yet. */
    record ChangePasswordRequest(String currentPassword, @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }

    record CodeRequest(@NotBlank @Size(max = 20) String code) {
    }

    record DeleteAccountRequest(String password, @NotBlank String confirmEmail) {
    }

    record RecoveryCodesResponse(List<String> recoveryCodes) {
    }

    @GetMapping
    ProfileResponse me(@AuthenticationPrincipal Jwt jwt) {
        return ProfileResponse.from(accounts.profile(CurrentUser.id(jwt)));
    }

    @PatchMapping
    ProfileResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
        return ProfileResponse.from(accounts.updateProfile(CurrentUser.id(jwt), request.fullName(),
                request.defaultCurrency(), request.locale(), request.timeZone(), request.monthStartDay()));
    }

    /** Deletes the account and, asynchronously, all of the user's data in every module. */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DeleteAccountRequest request) {
        accounts.delete(CurrentUser.id(jwt), request.password(), request.confirmEmail());
    }

    /** Signs every other device out. */
    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request) {
        accounts.changePassword(CurrentUser.id(jwt), CurrentUser.sessionId(jwt), request.currentPassword(),
                request.newPassword());
    }

    @PostMapping("/email/verification-code")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resendVerificationCode(@AuthenticationPrincipal Jwt jwt) {
        verification.resendEmailVerification(CurrentUser.id(jwt));
    }

    @PostMapping("/email/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void verifyEmail(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CodeRequest request) {
        verification.verifyEmail(CurrentUser.id(jwt), request.code());
    }

    @GetMapping("/sessions")
    List<SessionView> sessions(@AuthenticationPrincipal Jwt jwt) {
        return sessions.list(CurrentUser.id(jwt), CurrentUser.sessionId(jwt));
    }

    @DeleteMapping("/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeSession(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId) {
        sessions.revoke(CurrentUser.id(jwt), sessionId);
    }

    /** Signs every device out except this one. */
    @DeleteMapping("/sessions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeOtherSessions(@AuthenticationPrincipal Jwt jwt) {
        sessions.revokeAll(CurrentUser.id(jwt), CurrentUser.sessionId(jwt));
    }

    @PostMapping("/2fa/setup")
    TwoFactorSetup setupTwoFactor(@AuthenticationPrincipal Jwt jwt) {
        return twoFactor.setup(CurrentUser.id(jwt));
    }

    /** Returns ten one-time recovery codes; they are shown only this once. */
    @PostMapping("/2fa/enable")
    RecoveryCodesResponse enableTwoFactor(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CodeRequest request) {
        return new RecoveryCodesResponse(twoFactor.enable(CurrentUser.id(jwt), request.code()));
    }

    @PostMapping("/2fa/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void disableTwoFactor(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CodeRequest request) {
        twoFactor.disable(CurrentUser.id(jwt), request.code());
    }
}
