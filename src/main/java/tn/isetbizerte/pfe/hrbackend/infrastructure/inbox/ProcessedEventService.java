package tn.isetbizerte.pfe.hrbackend.infrastructure.inbox;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ProcessedEventService {

    private final ProcessedEventRepository repository;

    public ProcessedEventService(ProcessedEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public boolean tryMarkProcessed(String eventId, String source) {
        if (eventId == null || eventId.isBlank()) return true;
        if (repository.existsByEventId(eventId)) return false;
        try {
            repository.save(new ProcessedEvent(eventId, source));
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }

    @Transactional
    public void cleanupOlderThanDays(long days) {
        repository.deleteByProcessedAtBefore(LocalDateTime.now().minusDays(days));
    }
}
