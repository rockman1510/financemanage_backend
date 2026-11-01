package com.financemanage.notification.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferencesRepository extends JpaRepository<NotificationPreferences, UUID> {

    List<NotificationPreferences> findByDailyReminderEnabledTrue();
}
