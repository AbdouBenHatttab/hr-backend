package tn.isetbizerte.pfe.hrbackend.infrastructure.inbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {
    boolean existsByEventId(String eventId);

    void deleteByProcessedAtBefore(java.time.LocalDateTime cutoff);
}
