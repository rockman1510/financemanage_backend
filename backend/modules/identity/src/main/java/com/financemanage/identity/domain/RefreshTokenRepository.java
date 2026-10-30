package com.financemanage.identity.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByUserIdAndRevokedFalseAndExpiresAtAfterOrderByCreatedAtDesc(UUID userId, Instant now);

    List<RefreshToken> findBySessionIdAndRevokedFalse(UUID sessionId);

    List<RefreshToken> findByUserIdAndRevokedFalse(UUID userId);

    /**
     * Expired tokens, and rotated/revoked ones issued before {@code revokedCutoff}. Revoked tokens are kept a while
     * so that replaying a stolen old token is still detected (and ends its session).
     */
    @Modifying
    @Query("""
            delete from RefreshToken t
            where t.expiresAt < :now or (t.revoked = true and t.createdAt < :revokedCutoff)""")
    int deleteRevokedOrExpired(Instant now, Instant revokedCutoff);
}
