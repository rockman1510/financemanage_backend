package com.financemanage.identity.infrastructure.web;

import com.financemanage.identity.application.AuthService;
import com.financemanage.identity.application.LoginResult;
import com.financemanage.identity.application.TokenPair;
import com.financemanage.identity.application.VerificationService;
import com.financemanage.shared.web.ApiError;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public sign-up / sign-in endpoints. {@code deviceName} (e.g. "iPhone của An") labels the session in the device
 * list; the User-Agent is used when it is missing.
 */
@RestController
class AuthController {

    private final AuthService authService;
    private final VerificationService verification;

    AuthController(AuthService authService, VerificationService verification) {
        this.authService = authService;
        this.verification = verification;
    }

    record RegisterRequest(@NotBlank @Email String email, @NotBlank @Size(min = 8, max = 72) String password,
                           @NotBlank @Size(max = 100) String fullName, @Size(max = 100) String deviceName) {
    }

    record LoginRequest(@NotBlank String email, @NotBlank String password, @Size(max = 100) String deviceName) {
    }

    record GoogleLoginRequest(@NotBlank String idToken, @Size(max = 100) String deviceName) {
    }

    record TwoFactorLoginRequest(@NotBlank String challengeToken, @NotBlank @Size(max = 20) String code) {
    }

    record RefreshRequest(@NotBlank String refreshToken) {
    }

    record ForgotPasswordRequest(@NotBlank @Email String email) {
    }

    record ResetPasswordRequest(@NotBlank @Email String email, @NotBlank @Size(max = 10) String code,
                                @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }

    /**
     * Either the tokens, or {@code twoFactorRequired=true} with a {@code challengeToken} to send to
     * {@code /auth/2fa/verify} together with an authenticator code.
     */
    record LoginResponse(String accessToken, String refreshToken, Long expiresInSeconds, boolean twoFactorRequired,
                         String challengeToken, Long challengeExpiresInSeconds) {

        static LoginResponse from(LoginResult result) {
            if (result.twoFactorRequired()) {
                return new LoginResponse(null, null, null, true, result.challengeToken(),
                        result.challengeExpiresInSeconds());
            }
            TokenPair t = result.tokens();
            return new LoginResponse(t.accessToken(), t.refreshToken(), t.expiresInSeconds(), false, null, null);
        }
    }

    @PostMapping("/api/v1/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    TokenPair register(@Valid @RequestBody RegisterRequest request,
                       @RequestHeader(name = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return authService.register(request.email(), request.password(), request.fullName(),
                device(request.deviceName(), userAgent));
    }

    @PostMapping("/api/v1/auth/login")
    LoginResponse login(@Valid @RequestBody LoginRequest request,
                        @RequestHeader(name = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return LoginResponse.from(authService.login(request.email(), request.password(),
                device(request.deviceName(), userAgent)));
    }

    /** Signs in (or up) with the ID token the mobile app got from Google Sign-In. */
    @PostMapping("/api/v1/auth/google")
    LoginResponse google(@Valid @RequestBody GoogleLoginRequest request,
                         @RequestHeader(name = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return LoginResponse.from(authService.loginWithGoogle(request.idToken(),
                device(request.deviceName(), userAgent)));
    }

    @PostMapping("/api/v1/auth/2fa/verify")
    TokenPair completeTwoFactor(@Valid @RequestBody TwoFactorLoginRequest request) {
        return authService.completeTwoFactor(request.challengeToken(), request.code());
    }

    @PostMapping("/api/v1/auth/refresh")
    TokenPair refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/api/v1/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
    }

    /** Always 204 so the response does not tell whether the email is registered. */
    @PostMapping("/api/v1/auth/forgot-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        verification.requestPasswordReset(request.email());
    }

    @PostMapping("/api/v1/auth/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        verification.resetPassword(request.email(), request.code(), request.newPassword());
    }

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ApiError> badCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiError.of("INVALID_CREDENTIALS", ex.getMessage()));
    }

    private static String device(String deviceName, String userAgent) {
        return deviceName != null && !deviceName.isBlank() ? deviceName : userAgent;
    }
}
