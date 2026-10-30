package com.financemanage.identity.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface VerificationCodeRepository extends JpaRepository<VerificationCode, UUID> {

    Optional<VerificationCode> findFirstByUserIdAndPurposeOrderByCreatedAtDesc(UUID userId,
                                                                              VerificationPurpose purpose);

    List<VerificationCode> findByUserIdAndPurposeAndUsedAtIsNull(UUID userId, VerificationPurpose purpose);

    @Modifying
    @Query("delete from VerificationCode c where c.expiresAt < :before")
    int deleteExpiredBefore(Instant before);
}
