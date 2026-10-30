package com.financemanage.identity.application;

import com.financemanage.identity.domain.RefreshToken;
import com.financemanage.identity.domain.RefreshTokenRepository;
import com.financemanage.identity.domain.UserAccount;
import com.financemanage.identity.domain.UserAccountRepository;
import com.financemanage.shared.domain.NotFoundException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sessions = signed-in devices. Each session has one usable refresh token at a time; refreshing rotates it. Using
 * an already rotated token again means it was stolen (or replayed), so the whole session is revoked.
 */
@Service
@Transactional
public class SessionService {

    private static final int MAX_DEVICE_NAME_LENGTH = 100;

    private final RefreshTokenRepository refreshTokens;
    private final UserAccountRepository users;
    private final TokenIssuer tokenIssuer;
    private final Clock clock;
    private final Duration refreshTokenTtl;

    public SessionService(RefreshTokenRepository refreshTokens, UserAccountRepository users, TokenIssuer tokenIssuer,
                          Clock clock, @Value("${app.security.jwt.refresh-token-ttl:P30D}") Duration refreshTokenTtl) {
        this.refreshTokens = refreshTokens;
        this.users = users;
        this.tokenIssuer = tokenIssuer;
        this.clock = clock;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    /** Signs the user in on a new device. */
    public TokenPair start(UserAccount user, String deviceName) {
        return issue(user, UUID.randomUUID(), cleanDeviceName(deviceName));
    }

    /** Rotates the refresh token: the presented token is revoked and a new pair is issued for the same session. */
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public TokenPair refresh(String refreshToken) {
        RefreshToken stored = refreshTokens.findByTokenHash(Hashing.sha256(refreshToken))
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        if (stored.isRevoked()) {
            revokeSession(stored.getSessionId()); // reuse of a rotated token: end the session everywhere
            throw new BadCredentialsException("Invalid refresh token");
        }
        if (!stored.isUsable(clock.instant())) {
            throw new BadCredentialsException("Invalid refresh token");
        }
        stored.revoke();
        UserAccount user = users.findById(stored.getUserId())
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        return issue(user, stored.getSessionId(), stored.getDeviceName());
    }

    public void logout(String refreshToken) {
        refreshTokens.findByTokenHash(Hashing.sha256(refreshToken)).ifPresent(t -> revokeSession(t.getSessionId()));
    }

    @Transactional(readOnly = true)
    public List<SessionView> list(UUID userId, UUID currentSessionId) {
        return refreshTokens.findByUserIdAndRevokedFalseAndExpiresAtAfterOrderByCreatedAtDesc(userId,
                        clock.instant()).stream()
                .map(t -> new SessionView(t.getSessionId(), t.getDeviceName(), t.getCreatedAt(), t.getExpiresAt(),
                        t.getSessionId().equals(currentSessionId)))
                .toList();
    }

    /** Signs one device out. Its access token stays valid until it expires (15 minutes by default). */
    public void revoke(UUID userId, UUID sessionId) {
        List<RefreshToken> tokens = refreshTokens.findBySessionIdAndRevokedFalse(sessionId).stream()
                .filter(t -> t.getUserId().equals(userId)).toList();
        if (tokens.isEmpty()) {
            throw new NotFoundException("Session", sessionId);
        }
        tokens.forEach(RefreshToken::revoke);
    }

    /** Signs every device out except {@code keepSessionId} (may be null to sign out everywhere). */
    public void revokeAll(UUID userId, UUID keepSessionId) {
        refreshTokens.findByUserIdAndRevokedFalse(userId).stream()
                .filter(t -> !Objects.equals(t.getSessionId(), keepSessionId))
                .forEach(RefreshToken::revoke);
    }

    private void revokeSession(UUID sessionId) {
        refreshTokens.findBySessionIdAndRevokedFalse(sessionId).forEach(RefreshToken::revoke);
    }

    private TokenPair issue(UserAccount user, UUID sessionId, String deviceName) {
        String refresh = Hashing.randomToken();
        Instant now = clock.instant();
        refreshTokens.save(new RefreshToken(user.getId(), sessionId, Hashing.sha256(refresh), deviceName, now,
                now.plus(refreshTokenTtl)));
        return new TokenPair(tokenIssuer.issueAccessToken(user, sessionId), refresh,
                tokenIssuer.accessTokenTtlSeconds());
    }

    private static String cleanDeviceName(String deviceName) {
        if (deviceName == null || deviceName.isBlank()) {
            return null;
        }
        String name = deviceName.strip();
        return name.length() <= MAX_DEVICE_NAME_LENGTH ? name : name.substring(0, MAX_DEVICE_NAME_LENGTH);
    }
}
