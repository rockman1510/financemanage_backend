package com.financemanage.notification.infrastructure.web;

import com.financemanage.notification.application.NotificationService;
import com.financemanage.notification.domain.Notification;
import com.financemanage.notification.domain.NotificationType;
import com.financemanage.shared.security.CurrentUser;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController {

    private final NotificationService service;

    NotificationController(NotificationService service) {
        this.service = service;
    }

    record NotificationResponse(UUID id, NotificationType type, String title, String body, String referenceType,
                                UUID referenceId, Instant createdAt, boolean read) {

        static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getReferenceType(),
                    n.getReferenceId(), n.getCreatedAt(), n.getReadAt() != null);
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
}
