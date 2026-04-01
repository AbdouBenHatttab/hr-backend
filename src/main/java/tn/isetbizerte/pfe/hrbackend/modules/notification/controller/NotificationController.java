package tn.isetbizerte.pfe.hrbackend.modules.notification.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import tn.isetbizerte.pfe.hrbackend.modules.notification.service.NotificationService;

import java.util.HashMap;
import java.util.Map;

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
        String username = jwt.getClaimAsString("preferred_username");
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("data", notificationService.getMyNotifications(username));
        return res;
    }

    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER','HR_MANAGER')")
    @PatchMapping("/{id}/read")
    public Map<String, Object> markRead(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        notificationService.markAsRead(username, id);
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("message", "Notification marked as read");
        return res;
    }

    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER','HR_MANAGER')")
    @PatchMapping("/read-all")
    public Map<String, Object> markAllRead(@AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        notificationService.markAllRead(username);
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("message", "All notifications marked as read");
        return res;
    }
}
