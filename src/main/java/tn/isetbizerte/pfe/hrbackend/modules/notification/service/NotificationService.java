package tn.isetbizerte.pfe.hrbackend.modules.notification.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.isetbizerte.pfe.hrbackend.common.exception.ResourceNotFoundException;
import tn.isetbizerte.pfe.hrbackend.modules.notification.entity.Notification;
import tn.isetbizerte.pfe.hrbackend.modules.notification.repository.NotificationRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void createNotification(String userKeycloakId, String message, String type) {
        createNotification(userKeycloakId, message, type, null, null, null);
    }

    @Transactional
    public void createNotification(String userKeycloakId, String message, String type,
                                   String referenceType, Long referenceId, String actionUrl) {
        User user = userRepository.findByKeycloakId(userKeycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found for notification"));
        notificationRepository.save(new Notification(user, message, type, referenceType, referenceId, actionUrl));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getMyNotifications(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return notificationRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(n -> {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("id", n.getId());
                    m.put("title", humanizeType(n.getType()));
                    m.put("message", n.getMessage());
                    m.put("type", n.getType());
                    m.put("referenceType", n.getReferenceType());
                    m.put("referenceId", n.getReferenceId());
                    m.put("actionUrl", n.getActionUrl());
                    m.put("read", n.getRead());
                    m.put("createdAt", n.getCreatedAt());
                    return m;
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void markAsRead(String username, Long notificationId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));

        if (!n.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Cannot modify another user's notification");
        }

        n.setRead(true);
        notificationRepository.save(n);
    }

    @Transactional
    public void markAllRead(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<Notification> list = notificationRepository.findByUserOrderByCreatedAtDesc(user);
        for (Notification n : list) {
            n.setRead(true);
        }
        notificationRepository.saveAll(list);
    }

    @Transactional
    public void markBatchRead(String username, List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;

        // Avoid false 403s when the client accidentally sends duplicate IDs.
        List<Long> uniqueIds = ids.stream().distinct().toList();
        if (uniqueIds.isEmpty()) return;

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        long ownedCount = notificationRepository.countByIdInAndUser(uniqueIds, user);
        if (ownedCount != uniqueIds.size()) {
            throw new AccessDeniedException("Cannot modify another user's notification");
        }

        List<Notification> list = notificationRepository.findByIdInAndUser(uniqueIds, user);
        for (Notification n : list) {
            n.setRead(true);
        }
        notificationRepository.saveAll(list);
    }

    private String humanizeType(String type) {
        if (type == null || type.isBlank()) return "Notification";
        String[] words = type.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }
}
