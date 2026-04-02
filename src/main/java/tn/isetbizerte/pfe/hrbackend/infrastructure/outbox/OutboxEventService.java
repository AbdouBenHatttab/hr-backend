package tn.isetbizerte.pfe.hrbackend.infrastructure.outbox;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class OutboxEventService {

    private final OutboxEventRepository repository;

    public OutboxEventService(OutboxEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void enqueue(String topic, String eventKey, String payload) {
        OutboxEvent event = new OutboxEvent(topic, eventKey, payload);
        repository.save(event);
    }

    @Transactional
    public void markSent(OutboxEvent event) {
        event.setStatus(OutboxStatus.SENT);
        event.setSentAt(LocalDateTime.now());
        event.setNextRetryAt(null);
        repository.save(event);
    }

    @Transactional
    public void markFailed(OutboxEvent event, String error) {
        event.setAttempts(event.getAttempts() + 1);
        event.setLastError(error);
        event.setStatus(OutboxStatus.FAILED);
        event.setNextRetryAt(null);
        repository.save(event);
    }

    @Transactional
    public void markPending(OutboxEvent event, String error, LocalDateTime nextRetryAt) {
        event.setAttempts(event.getAttempts() + 1);
        event.setLastError(error);
        event.setStatus(OutboxStatus.PENDING);
        event.setNextRetryAt(nextRetryAt);
        repository.save(event);
    }

    @Transactional
    public void replay(Long eventId) {
        OutboxEvent event = repository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Outbox event not found: " + eventId));
        event.setStatus(OutboxStatus.PENDING);
        event.setNextRetryAt(LocalDateTime.now());
        event.setLastError(null);
        event.setAttempts(0);
        repository.save(event);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> counts() {
        return Map.of(
                "pending", repository.countByStatus(OutboxStatus.PENDING),
                "sent", repository.countByStatus(OutboxStatus.SENT),
                "failed", repository.countByStatus(OutboxStatus.FAILED)
        );
    }
}
