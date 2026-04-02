package tn.isetbizerte.pfe.hrbackend.infrastructure.inbox;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "processed_events", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"event_id"})
})
public class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Column(name = "source", length = 200)
    private String source;

    @Column(nullable = false)
    private LocalDateTime processedAt = LocalDateTime.now();

    public ProcessedEvent() {}

    public ProcessedEvent(String eventId, String source) {
        this.eventId = eventId;
        this.source = source;
        this.processedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }
}
