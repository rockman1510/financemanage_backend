package com.financemanage.identity.application;

import com.financemanage.identity.domain.RefreshToken;
import com.financemanage.identity.domain.RefreshTokenRepository;
import com.financemanage.identity.domain.UserAccount;
import com.financemanage.identity.domain.UserAccountRepository;
import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.NotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserAccountRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;
    private final Clock clock;
    private final Duration refreshTokenTtl;

    public AuthService(UserAccountRepository users, RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder, TokenIssuer tokenIssuer, Clock clock,
                       @Value("${app.security.jwt.refresh-token-ttl:P30D}") Duration refreshTokenTtl) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.clock = clock;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    public TokenPair register(String email, String password, String fullName) {
        if (users.existsByEmail(UserAccount.normalize(email))) {
            throw new DomainException("EMAIL_TAKEN", "Email is already registered");
        }
        UserAccount user = users.save(new UserAccount(email, passwordEncoder.encode(password), fullName));
        return issueTokens(user);
    }

    public TokenPair login(String email, String password) {
        UserAccount user = users.findByEmail(UserAccount.normalize(email))
                .filter(u -> passwordEncoder.matches(password, u.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        return issueTokens(user);
    }

    /** Rotates the refresh token: the presented token is revoked and a new pair is issued. */
    public TokenPair refresh(String refreshToken) {
        RefreshToken stored = refreshTokens.findByTokenHash(hash(refreshToken))
                .filter(t -> t.isUsable(clock.instant()))
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        stored.revoke();
        UserAccount user = users.findById(stored.getUserId())
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        return issueTokens(user);
    }

    public void logout(String refreshToken) {
        refreshTokens.findByTokenHash(hash(refreshToken)).ifPresent(RefreshToken::revoke);
    }

    @Transactional(readOnly = true)
    public UserAccount profile(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("User", userId));
    }

    private TokenPair issueTokens(UserAccount user) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = clock.instant().plus(refreshTokenTtl);
        refreshTokens.save(new RefreshToken(user.getId(), hash(refresh), expiresAt));
        return new TokenPair(tokenIssuer.issueAccessToken(user), refresh, tokenIssuer.accessTokenTtlSeconds());
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
