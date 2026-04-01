package tn.isetbizerte.pfe.hrbackend.modules.history.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "request_history")
public class RequestHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long requestId;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(nullable = false, length = 30)
    private String action;

    @Column(nullable = false, length = 80)
    private String actorId;

    @Column(length = 2000)
    private String comment;

    @Column(nullable = false)
    private LocalDateTime timestamp = LocalDateTime.now();

    public RequestHistory() {}

    public RequestHistory(Long requestId, String type, String action, String actorId, String comment) {
        this.requestId = requestId;
        this.type = type;
        this.action = action;
        this.actorId = actorId;
        this.comment = comment;
        this.timestamp = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
