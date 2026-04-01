package tn.isetbizerte.pfe.hrbackend.common.event;

import java.time.Instant;

public class DecisionEvent {
    private String type;
    private Long requestId;
    private String employeeId;
    private String actorId;
    private String timestamp;

    public DecisionEvent() {}

    public DecisionEvent(String type, Long requestId, String employeeId, String actorId) {
        this.type = type;
        this.requestId = requestId;
        this.employeeId = employeeId;
        this.actorId = actorId;
        this.timestamp = Instant.now().toString();
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getRequestId() {
        return requestId;
    }

    public void setRequestId(Long requestId) {
        this.requestId = requestId;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
