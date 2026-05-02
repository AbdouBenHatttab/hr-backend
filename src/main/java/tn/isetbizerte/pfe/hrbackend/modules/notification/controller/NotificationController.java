package tn.isetbizerte.pfe.hrbackend.modules.notification.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import tn.isetbizerte.pfe.hrbackend.modules.notification.dto.NotificationBatchReadRequest;
import tn.isetbizerte.pfe.hrbackend.modules.notification.service.NotificationService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER','HR_MANAGER')")
    @GetMapping
    public Map<String, Object> getMyNotifications(@AuthenticationPrincipal Jwt jwt) {
        String keycloakId = jwt.getSubject();
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("data", notificationService.getMyNotifications(keycloakId));
        return res;
    }

    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER','HR_MANAGER')")
    @GetMapping("/{id}/details")
    public Map<String, Object> getNotificationDetails(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        String keycloakId = jwt.getSubject();
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("data", notificationService.getNotificationDetails(keycloakId, id));
        return res;
    }

    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER','HR_MANAGER')")
    @PatchMapping("/{id}/read")
    public Map<String, Object> markRead(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        String keycloakId = jwt.getSubject();
        notificationService.markAsRead(keycloakId, id);
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("message", "Notification marked as read");
        return res;
    }

    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER','HR_MANAGER')")
    @PatchMapping("/read-all")
    public Map<String, Object> markAllRead(@AuthenticationPrincipal Jwt jwt) {
        String keycloakId = jwt.getSubject();
        notificationService.markAllRead(keycloakId);
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("message", "All notifications marked as read");
        return res;
    }

    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER','HR_MANAGER')")
    @PatchMapping("/read-batch")
    public Map<String, Object> markBatchRead(@RequestBody NotificationBatchReadRequest body, @AuthenticationPrincipal Jwt jwt) {
        String keycloakId = jwt.getSubject();
        List<Object> idsObj = body != null ? body.getIds() : null;
        List<Long> ids = idsObj != null
                ? idsObj.stream()
                    .filter(Number.class::isInstance)
                    .map(v -> ((Number) v).longValue())
                    .collect(Collectors.toList())
                : List.of();
        notificationService.markBatchRead(keycloakId, ids);
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("message", "Notifications marked as read");
        return res;
    }
}
