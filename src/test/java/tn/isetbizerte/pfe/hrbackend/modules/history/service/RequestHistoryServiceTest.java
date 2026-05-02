package tn.isetbizerte.pfe.hrbackend.modules.history.service;

import org.junit.jupiter.api.Test;
import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;
import tn.isetbizerte.pfe.hrbackend.modules.history.entity.RequestHistory;
import tn.isetbizerte.pfe.hrbackend.modules.history.repository.RequestHistoryRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.Person;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestHistoryServiceTest {

    private final RequestHistoryRepository repository = mock(RequestHistoryRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final RequestHistoryService service = new RequestHistoryService(repository, userRepository);

    @Test
    void getHistory_queriesByTypeAndRequestIdSoCollidingIdsDoNotMix() {
        when(repository.findByTypeAndRequestIdOrderByTimestampDesc("LOAN", 7L)).thenReturn(List.of());

        service.getHistory("LOAN", 7L);

        verify(repository).findByTypeAndRequestIdOrderByTimestampDesc("LOAN", 7L);
    }

    @Test
    void getHistory_resolvesActorFullNameAndRole() {
        RequestHistory history = history("DOCUMENT", 10L, "HR_APPROVED", "actor-1", "PENDING", "APPROVED");
        User actor = new User("actor-1", "jsmith");
        actor.setRole(TypeRole.HR_MANAGER);
        actor.setPerson(new Person("Smith", "Jane", "jane@example.test"));
        when(repository.findByTypeAndRequestIdOrderByTimestampDesc("DOCUMENT", 10L)).thenReturn(List.of(history));
        when(userRepository.findByKeycloakId("actor-1")).thenReturn(Optional.of(actor));

        var result = service.getHistory("DOCUMENT", 10L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).actorUsername()).isEqualTo("jsmith");
        assertThat(result.get(0).actorDisplayName()).isEqualTo("Jane Smith");
        assertThat(result.get(0).actorRole()).isEqualTo("HR_MANAGER");
    }

    @Test
    void getHistory_fallsBackToRawActorIdWhenUserIsMissing() {
        RequestHistory history = history("AUTH", 3L, "EMPLOYEE_CANCELLED", "missing-actor", "PENDING", "CANCELLED");
        when(repository.findByTypeAndRequestIdOrderByTimestampDesc("AUTH", 3L)).thenReturn(List.of(history));
        when(userRepository.findByKeycloakId("missing-actor")).thenReturn(Optional.empty());

        var result = service.getHistory("AUTH", 3L);

        assertThat(result.get(0).actorUsername()).isNull();
        assertThat(result.get(0).actorDisplayName()).isEqualTo("missing-actor");
        assertThat(result.get(0).actorRole()).isNull();
    }

    @Test
    void getHistory_returnsRepositoryOrderedHistory() {
        RequestHistory newer = history("LOAN", 1L, "HR_APPROVED", "actor", "PENDING", "APPROVED");
        newer.setTimestamp(LocalDateTime.of(2026, 5, 1, 16, 0));
        RequestHistory older = history("LOAN", 1L, "CREATED", "actor", null, "PENDING");
        older.setTimestamp(LocalDateTime.of(2026, 5, 1, 9, 0));
        when(repository.findByTypeAndRequestIdOrderByTimestampDesc("LOAN", 1L)).thenReturn(List.of(newer, older));
        when(userRepository.findByKeycloakId("actor")).thenReturn(Optional.empty());

        var result = service.getHistory("LOAN", 1L);

        assertThat(result).extracting("action").containsExactly("HR_APPROVED", "CREATED");
    }

    private RequestHistory history(String type, Long requestId, String action, String actorId, String from, String to) {
        return new RequestHistory(requestId, type, action, actorId, "comment", from, to);
    }
}
