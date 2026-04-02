package tn.isetbizerte.pfe.hrbackend.common.event;

import java.time.Instant;

public class RequestEvent {
    private String eventId;
    private String type;
    private Long requestId;
    private String requestType;
    private String employeeId;
    private String actorId;
    private String timestamp;
    private String comment;

    public RequestEvent() {}

    public RequestEvent(String type, String requestType, Long requestId, String employeeId, String actorId, String comment) {
        this.eventId = java.util.UUID.randomUUID().toString();
        this.type = type;
        this.requestType = requestType;
        this.requestId = requestId;
        this.employeeId = employeeId;
        this.actorId = actorId;
        this.comment = comment;
        this.timestamp = Instant.now().toString();
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }

    public String getRequestType() { return requestType; }
    public void setRequestType(String requestType) { this.requestType = requestType; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
