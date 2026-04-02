package tn.isetbizerte.pfe.hrbackend.common.event;

import java.time.Instant;

public class NotificationEvent {
    private String eventId;
    private String userId;
    private String message;
    private String type;
    private String referenceType;
    private Long referenceId;
    private String actionUrl;
    private String timestamp;

    public NotificationEvent() {}

    public NotificationEvent(String userId, String message, String type) {
        this.eventId = java.util.UUID.randomUUID().toString();
        this.userId = userId;
        this.message = message;
        this.type = type;
        this.timestamp = Instant.now().toString();
    }

    public NotificationEvent(String userId, String message, String type,
                             String referenceType, Long referenceId, String actionUrl) {
        this.eventId = java.util.UUID.randomUUID().toString();
        this.userId = userId;
        this.message = message;
        this.type = type;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.actionUrl = actionUrl;
        this.timestamp = Instant.now().toString();
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getReferenceType() { return referenceType; }
    public void setReferenceType(String referenceType) { this.referenceType = referenceType; }

    public Long getReferenceId() { return referenceId; }
    public void setReferenceId(Long referenceId) { this.referenceId = referenceId; }

    public String getActionUrl() { return actionUrl; }
    public void setActionUrl(String actionUrl) { this.actionUrl = actionUrl; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}
