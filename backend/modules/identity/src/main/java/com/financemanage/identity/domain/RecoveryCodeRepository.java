package com.financemanage.identity.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, UUID> {

    Optional<RecoveryCode> findByUserIdAndCodeHashAndUsedAtIsNull(UUID userId, String codeHash);

    List<RecoveryCode> findByUserIdAndUsedAtIsNull(UUID userId);

    @Modifying
    @Query("delete from RecoveryCode c where c.userId = :userId")
    void deleteByUserId(UUID userId);
}
