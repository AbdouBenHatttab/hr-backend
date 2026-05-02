package tn.isetbizerte.pfe.hrbackend.modules.history.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.isetbizerte.pfe.hrbackend.modules.history.dto.RequestHistoryDto;
import tn.isetbizerte.pfe.hrbackend.modules.history.entity.RequestHistory;
import tn.isetbizerte.pfe.hrbackend.modules.history.repository.RequestHistoryRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;

import java.util.List;

@Service
public class RequestHistoryService {

    private final RequestHistoryRepository repository;
    private final UserRepository userRepository;

    public RequestHistoryService(RequestHistoryRepository repository, UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void record(String type, String action, Long requestId, String actorId, String comment) {
        repository.save(new RequestHistory(requestId, type, action, actorId, comment));
    }

    @Transactional
    public void record(String type, String action, Long requestId, String actorId, String comment,
                       String fromState, String toState) {
        repository.save(new RequestHistory(requestId, type, action, actorId, comment, fromState, toState));
    }

    @Transactional(readOnly = true)
    public boolean exists(String type, String action, Long requestId, String actorId) {
        return repository.existsByRequestIdAndTypeAndActionAndActorId(requestId, type, action, actorId);
    }

    @Transactional(readOnly = true)
    public List<RequestHistoryDto> getHistory(String type, Long requestId) {
        return repository.findByTypeAndRequestIdOrderByTimestampDesc(type, requestId).stream()
                .map(this::toDto)
                .toList();
    }

    private RequestHistoryDto toDto(RequestHistory history) {
        ActorDisplay actor = resolveActor(history.getActorId());
        return new RequestHistoryDto(
                history.getId(),
                history.getRequestId(),
                history.getType(),
                history.getAction(),
                history.getFromState(),
                history.getToState(),
                history.getComment(),
                history.getTimestamp(),
                history.getActorId(),
                actor.username(),
                actor.displayName(),
                actor.role()
        );
    }

    private ActorDisplay resolveActor(String actorId) {
        if (actorId == null || actorId.isBlank()) {
            return new ActorDisplay(null, null, null);
        }

        return userRepository.findByKeycloakId(actorId)
                .map(user -> new ActorDisplay(
                        user.getUsername(),
                        displayName(user, actorId),
                        user.getRole() != null ? user.getRole().name() : null
                ))
                .orElseGet(() -> new ActorDisplay(null, actorId, null));
    }

    private String displayName(User user, String actorId) {
        if (user.getPerson() != null) {
            String firstName = user.getPerson().getFirstName() != null ? user.getPerson().getFirstName().trim() : "";
            String lastName = user.getPerson().getLastName() != null ? user.getPerson().getLastName().trim() : "";
            String fullName = (firstName + " " + lastName).trim();
            if (!fullName.isBlank()) return fullName;
        }
        if (user.getUsername() != null && !user.getUsername().isBlank()) return user.getUsername();
        return actorId;
    }

    private record ActorDisplay(String username, String displayName, String role) {
    }
}
