package tn.isetbizerte.pfe.hrbackend.modules.history.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.isetbizerte.pfe.hrbackend.modules.history.entity.RequestHistory;

import java.util.List;

@Repository
public interface RequestHistoryRepository extends JpaRepository<RequestHistory, Long> {
    List<RequestHistory> findByRequestIdOrderByTimestampDesc(Long requestId);

    boolean existsByRequestIdAndTypeAndActionAndActorId(Long requestId, String type, String action, String actorId);
}
