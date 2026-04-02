package tn.isetbizerte.pfe.hrbackend.infrastructure.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM OutboxEvent e " +
           "WHERE e.status = :status " +
           "AND (e.nextRetryAt IS NULL OR e.nextRetryAt <= :now) " +
           "ORDER BY e.createdAt ASC")
    List<OutboxEvent> findReadyByStatusOrderByCreatedAtAsc(OutboxStatus status, LocalDateTime now, Pageable pageable);

    long countByStatus(OutboxStatus status);

    List<OutboxEvent> findByStatus(OutboxStatus status);
}
