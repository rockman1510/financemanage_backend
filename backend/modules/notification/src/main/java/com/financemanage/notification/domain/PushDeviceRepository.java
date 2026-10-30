package com.financemanage.notification.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PushDeviceRepository extends JpaRepository<PushDevice, String> {

    List<PushDevice> findByOwnerId(UUID ownerId);

    @Modifying
    @Query("delete from PushDevice d where d.ownerId = :ownerId")
    void deleteByOwnerId(UUID ownerId);
}
