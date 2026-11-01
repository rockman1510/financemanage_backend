package com.financemanage.notification.infrastructure.web;

import com.financemanage.notification.application.NotificationService;
import com.financemanage.notification.application.PreferencesService;
import com.financemanage.notification.application.PushDeviceService;
import com.financemanage.notification.domain.Notification;
import com.financemanage.notification.domain.NotificationPreferences;
import com.financemanage.notification.domain.NotificationType;
import com.financemanage.notification.domain.PushDevice;
import com.financemanage.shared.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController {

    private final NotificationService service;
    private final PreferencesService preferences;
    private final PushDeviceService devices;

    NotificationController(NotificationService service, PreferencesService preferences, PushDeviceService devices) {
        this.service = service;
        this.preferences = preferences;
        this.devices = devices;
    }

    record NotificationResponse(UUID id, NotificationType type, String title, String body, String referenceType,
                                UUID referenceId, Instant createdAt, boolean read) {

        static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getReferenceType(),
                    n.getReferenceId(), n.getCreatedAt(), n.getReadAt() != null);
        }
    }

    /** {@code platform}: "ios", "android"…; {@code deviceName} labels it in the list. */
    record RegisterDeviceRequest(@NotBlank @Size(max = 200) String token, @Size(max = 10) String platform,
                                 @Size(max = 100) String deviceName) {
    }

    record DeviceResponse(String token, String platform, String deviceName, Instant registeredAt) {

        static DeviceResponse from(PushDevice d) {
            return new DeviceResponse(d.getToken(), d.getPlatform(), d.getDeviceName(), d.getRegisteredAt());
        }
    }

    /**
     * {@code disabledTypes}: types not notified at all; {@code dailyReminderTime}: the hour of the "record your
     * spending" reminder; {@code quietStart}/{@code quietEnd}: no push in between (kept in the app), may cross
     * midnight.
     */
    record PreferencesRequest(Set<NotificationType> disabledTypes, Boolean dailyReminderEnabled,
                              LocalTime dailyReminderTime, LocalTime quietStart, LocalTime quietEnd) {
    }

    record PreferencesResponse(Set<NotificationType> disabledTypes, boolean dailyReminderEnabled,
                               LocalTime dailyReminderTime, LocalTime quietStart, LocalTime quietEnd,
                               String timeZone) {

        static PreferencesResponse from(NotificationPreferences p) {
            return new PreferencesResponse(p.getDisabledTypes(), p.isDailyReminderEnabled(), p.getDailyReminderTime(),
                    p.getQuietStart(), p.getQuietEnd(), p.getTimeZone());
        }
    }

    @GetMapping
    List<NotificationResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.latest(CurrentUser.id(jwt)).stream().map(NotificationResponse::from).toList();
    }

    @GetMapping("/unread-count")
    Map<String, Long> unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("count", service.unreadCount(CurrentUser.id(jwt)));
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.markRead(id, CurrentUser.id(jwt));
    }

    @PostMapping("/read-all")
    Map<String, Integer> markAllRead(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("marked", service.markAllRead(CurrentUser.id(jwt)));
    }

    @GetMapping("/preferences")
    PreferencesResponse preferences(@AuthenticationPrincipal Jwt jwt) {
        return PreferencesResponse.from(preferences.get(CurrentUser.id(jwt)));
    }

    /** Replaces the settings; omitted values fall back to the defaults (everything on, reminder at 20:00). */
    @PutMapping("/preferences")
    PreferencesResponse updatePreferences(@AuthenticationPrincipal Jwt jwt,
                                          @RequestBody PreferencesRequest request) {
        return PreferencesResponse.from(preferences.update(CurrentUser.id(jwt), request.disabledTypes(),
                !Boolean.FALSE.equals(request.dailyReminderEnabled()), request.dailyReminderTime(),
                request.quietStart(), request.quietEnd()));
    }

    @GetMapping("/devices")
    List<DeviceResponse> devices(@AuthenticationPrincipal Jwt jwt) {
        return devices.list(CurrentUser.id(jwt)).stream().map(DeviceResponse::from).toList();
    }

    @PostMapping("/devices")
    @ResponseStatus(HttpStatus.CREATED)
    DeviceResponse register(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RegisterDeviceRequest request) {
        return DeviceResponse.from(devices.register(CurrentUser.id(jwt), request.token(), request.platform(),
                request.deviceName()));
    }

    @DeleteMapping("/devices/{token}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unregister(@AuthenticationPrincipal Jwt jwt, @PathVariable String token) {
        devices.unregister(CurrentUser.id(jwt), token);
    }
}
