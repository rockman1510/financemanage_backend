package com.financemanage.notification.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findTop50ByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    long countByOwnerIdAndReadAtIsNull(UUID ownerId);

    Optional<Notification> findByIdAndOwnerId(UUID id, UUID ownerId);

    @Modifying
    @Query("delete from Notification n where n.ownerId = :ownerId")
    void deleteByOwnerId(UUID ownerId);
}
