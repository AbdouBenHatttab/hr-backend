package tn.isetbizerte.pfe.hrbackend.infrastructure.outbox;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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
        repository.save(event);
    }

    @Transactional
    public void markFailed(OutboxEvent event, String error) {
        event.setAttempts(event.getAttempts() + 1);
        event.setLastError(error);
        event.setStatus(OutboxStatus.FAILED);
        repository.save(event);
    }

    @Transactional
    public void markPending(OutboxEvent event, String error) {
        event.setAttempts(event.getAttempts() + 1);
        event.setLastError(error);
        event.setStatus(OutboxStatus.PENDING);
        repository.save(event);
    }
}
