package com.financemanage.identity.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface LoginChallengeRepository extends JpaRepository<LoginChallenge, UUID> {

    Optional<LoginChallenge> findByTokenHash(String tokenHash);

    @Modifying
    @Query("delete from LoginChallenge c where c.expiresAt < :before")
    int deleteExpiredBefore(Instant before);
}
