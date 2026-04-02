package tn.isetbizerte.pfe.hrbackend.modules.history.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.isetbizerte.pfe.hrbackend.modules.history.entity.RequestHistory;
import tn.isetbizerte.pfe.hrbackend.modules.history.repository.RequestHistoryRepository;

@Service
public class RequestHistoryService {

    private final RequestHistoryRepository repository;

    public RequestHistoryService(RequestHistoryRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(String type, String action, Long requestId, String actorId, String comment) {
        repository.save(new RequestHistory(requestId, type, action, actorId, comment));
    }

    @Transactional(readOnly = true)
    public boolean exists(String type, String action, Long requestId, String actorId) {
        return repository.existsByRequestIdAndTypeAndActionAndActorId(requestId, type, action, actorId);
    }
}
