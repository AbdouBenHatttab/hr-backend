package tn.isetbizerte.pfe.hrbackend.modules.notification.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.isetbizerte.pfe.hrbackend.common.exception.ResourceNotFoundException;
import tn.isetbizerte.pfe.hrbackend.modules.notification.entity.Notification;
import tn.isetbizerte.pfe.hrbackend.modules.notification.repository.NotificationRepository;
import tn.isetbizerte.pfe.hrbackend.modules.requests.entity.AuthorizationRequest;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.AuthorizationRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final AuthorizationRequestRepository authorizationRequestRepository;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository,
                               AuthorizationRequestRepository authorizationRequestRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.authorizationRequestRepository = authorizationRequestRepository;
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
        if (referenceType != null && referenceId != null
                && notificationRepository.existsByUserAndTypeAndReferenceTypeAndReferenceId(
                        user, type, referenceType, referenceId)) {
            return;
        }
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
                    m.put("detailsAvailable", hasDetails(n));
                    m.put("read", n.getRead());
                    m.put("createdAt", n.getCreatedAt());
                    return m;
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getNotificationDetails(String username, Long notificationId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));

        if (!notification.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Cannot view another user's notification");
        }

        if ("AUTH".equalsIgnoreCase(notification.getReferenceType()) && notification.getReferenceId() != null) {
            return buildAuthorizationDetails(notification);
        }

        Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("type", notification.getReferenceType());
        details.put("title", humanizeType(notification.getType()));
        details.put("summary", notification.getMessage());
        details.put("actionUrl", notification.getActionUrl());
        details.put("fields", List.of(
                field("Notification Type", humanizeType(notification.getType())),
                field("Created At", notification.getCreatedAt()),
                field("Message", notification.getMessage())
        ));
        return details;
    }

    @Transactional
    public void markAsRead(String username, Long notificationId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));

        if (!n.getUser().getId().equals(user.getId())) {
            throw new   AccessDeniedException("Cannot modify another user's notification");
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

        // Load the notifications that actually exist (some IDs may be stale if notifications were deleted).
        List<Notification> found = notificationRepository.findAllById(uniqueIds);

        // Mark only the current user's notifications (ignore foreign and unknown/stale IDs).
        List<Notification> list = found.stream()
                .filter(n -> n.getUser() != null && n.getUser().getId().equals(user.getId()))
                .toList();
        if (list.isEmpty()) return;
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

    private boolean hasDetails(Notification notification) {
        return notification.getReferenceType() != null
                && notification.getReferenceId() != null
                && "AUTH".equalsIgnoreCase(notification.getReferenceType());
    }

    private Map<String, Object> buildAuthorizationDetails(Notification notification) {
        AuthorizationRequest request = authorizationRequestRepository.findById(notification.getReferenceId())
                .orElseThrow(() -> new ResourceNotFoundException("Authorization request not found"));

        boolean rejected = request.getStatus() != null && "REJECTED".equals(request.getStatus().name());
        String statusLabel = formatEnum(request.getStatus() != null ? request.getStatus().name() : null);
        String typeLabel = formatEnum(request.getAuthorizationType() != null ? request.getAuthorizationType().name() : null);
        String period = formatPeriod(request.getStartDate(), request.getEndDate());
        String summary = rejected
                ? "Your " + typeLabel + " authorization request was rejected."
                : "Your " + typeLabel + " authorization request was approved.";

        List<Map<String, Object>> fields = new java.util.ArrayList<>();
        fields.add(field("Authorization Type", typeLabel));
        fields.add(field("Submitted Date", request.getRequestedAt()));
        fields.add(field("Processed Date", request.getProcessedAt()));
        fields.add(field("Requested Period", period));
        fields.add(field("Final Status", statusLabel));
        if (rejected || (request.getHrNote() != null && !request.getHrNote().isBlank())) {
            fields.add(field(rejected ? "Rejection Reason" : "HR Note", request.getHrNote()));
        }
        fields.add(field("Summary", summary));

        Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("type", "AUTH");
        details.put("title", rejected ? "Authorization Request Rejected" : "Authorization Request Approved");
        details.put("summary", summary);
        details.put("status", request.getStatus() != null ? request.getStatus().name() : null);
        details.put("actionUrl", notification.getActionUrl());
        details.put("fields", fields);
        return details;
    }

    private Map<String, Object> field(String label, Object value) {
        Map<String, Object> field = new java.util.LinkedHashMap<>();
        field.put("label", label);
        field.put("value", value != null ? value : "—");
        return field;
    }

    private String formatPeriod(LocalDate startDate, LocalDate endDate) {
        if (startDate == null && endDate == null) return "Not specified";
        if (startDate != null && endDate != null) return startDate + " to " + endDate;
        if (startDate != null) return "From " + startDate;
        return "Until " + endDate;
    }

    private String formatEnum(String value) {
        if (value == null || value.isBlank()) return "—";
        String[] words = value.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }
}
