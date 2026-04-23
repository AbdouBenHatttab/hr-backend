package tn.isetbizerte.pfe.hrbackend.modules.requests.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import tn.isetbizerte.pfe.hrbackend.common.enums.AuthorizationType;
import tn.isetbizerte.pfe.hrbackend.common.enums.RequestStatus;
import tn.isetbizerte.pfe.hrbackend.infrastructure.kafka.producer.RequestEventProducer;
import tn.isetbizerte.pfe.hrbackend.infrastructure.storage.DocumentAttachmentStorageService;
import tn.isetbizerte.pfe.hrbackend.modules.calendar.service.WorkingDayService;
import tn.isetbizerte.pfe.hrbackend.modules.employee.repository.LeaveRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.history.service.RequestHistoryService;
import tn.isetbizerte.pfe.hrbackend.modules.requests.entity.AuthorizationRequest;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.AuthorizationRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.DocumentRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.LoanRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.StoredEmployeeDocumentRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.Person;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.PersonRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.service.AuthenticatedUserResolver;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequestsServiceAuthorizationNormalizationTest {

    @Test
    void getAllAuthRequests_normalizesLegacyTypesBeforeReadingRows() {
        AuthorizationRequestRepository authRepo = mock(AuthorizationRequestRepository.class);
        RequestsService service = new RequestsService(
                mock(DocumentRequestRepository.class),
                mock(StoredEmployeeDocumentRepository.class),
                mock(LoanRequestRepository.class),
                authRepo,
                mock(UserRepository.class),
                mock(PersonRepository.class),
                mock(AuthenticatedUserResolver.class),
                mock(LoanScoreEngine.class),
                mock(RequestEventProducer.class),
                mock(RequestHistoryService.class),
                mock(DocumentAttachmentStorageService.class),
                mock(LeaveRequestRepository.class),
                mock(WorkingDayService.class)
        );
        AuthorizationRequest request = validAuthorizationRequest();
        PageRequest pageable = PageRequest.of(0, 10);
        when(authRepo.normalizeLegacyAuthorizationTypes()).thenReturn(2);
        when(authRepo.findAllByOrderByRequestedAtDesc(pageable)).thenReturn(new PageImpl<>(List.of(request)));

        var result = service.getAllAuthRequests(pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0))
                .extracting(row -> row.get("authorizationType"), row -> row.get("authorizationTypeLabel"))
                .containsExactly("TIME_PERMISSION", "Time Permission");
        var inOrder = inOrder(authRepo);
        inOrder.verify(authRepo).normalizeLegacyAuthorizationTypes();
        inOrder.verify(authRepo).findAllByOrderByRequestedAtDesc(pageable);
    }

    @Test
    void authorizationTypeEnum_noLongerAcceptsRemovedTypes() {
        assertThat(AuthorizationType.values()).containsExactly(
                AuthorizationType.BUSINESS_TRIP,
                AuthorizationType.TRAINING,
                AuthorizationType.TIME_PERMISSION
        );
    }

    private AuthorizationRequest validAuthorizationRequest() {
        Person person = new Person();
        person.setFirstName("Amina");
        person.setLastName("Ben Ali");
        person.setEmail("amina@example.test");

        User user = new User();
        user.setId(1L);
        user.setUsername("amina");
        user.setKeycloakId("kc-amina");
        user.setPerson(person);

        AuthorizationRequest request = new AuthorizationRequest();
        request.setId(10L);
        request.setUser(user);
        request.setAuthorizationType(AuthorizationType.TIME_PERMISSION);
        request.setStatus(RequestStatus.PENDING);
        return request;
    }
}
