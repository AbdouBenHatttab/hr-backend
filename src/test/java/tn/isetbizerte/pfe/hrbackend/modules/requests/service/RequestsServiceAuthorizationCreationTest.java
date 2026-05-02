package tn.isetbizerte.pfe.hrbackend.modules.requests.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.Jwt;
import tn.isetbizerte.pfe.hrbackend.common.enums.AuthorizationType;
import tn.isetbizerte.pfe.hrbackend.common.exception.BadRequestException;
import tn.isetbizerte.pfe.hrbackend.infrastructure.kafka.producer.RequestEventProducer;
import tn.isetbizerte.pfe.hrbackend.infrastructure.storage.DocumentAttachmentStorageService;
import tn.isetbizerte.pfe.hrbackend.modules.calendar.service.WorkingDayService;
import tn.isetbizerte.pfe.hrbackend.modules.employee.repository.LeaveRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.history.service.RequestHistoryService;
import tn.isetbizerte.pfe.hrbackend.modules.requests.dto.CreateAuthorizationRequestDto;
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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RequestsServiceAuthorizationCreationTest {

    private AuthorizationRequestRepository authRepo;
    private AuthenticatedUserResolver authenticatedUserResolver;
    private RequestsService service;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        authRepo = mock(AuthorizationRequestRepository.class);
        authenticatedUserResolver = mock(AuthenticatedUserResolver.class);
        service = new RequestsService(
                mock(DocumentRequestRepository.class),
                mock(StoredEmployeeDocumentRepository.class),
                mock(LoanRequestRepository.class),
                authRepo,
                mock(UserRepository.class),
                mock(PersonRepository.class),
                authenticatedUserResolver,
                mock(LoanScoreEngine.class),
                mock(RequestEventProducer.class),
                mock(RequestHistoryService.class),
                mock(DocumentAttachmentStorageService.class),
                mock(LeaveRequestRepository.class),
                mock(WorkingDayService.class)
        );
        jwt = mock(Jwt.class);
        when(authenticatedUserResolver.require(jwt)).thenReturn(user());
    }

    @Test
    void equipmentRequest_createsSuccessfullyWithRequiredFields() {
        CreateAuthorizationRequestDto body = equipmentRequest();

        var result = service.createAuthRequest(jwt, body);

        ArgumentCaptor<AuthorizationRequest> captor = ArgumentCaptor.forClass(AuthorizationRequest.class);
        verify(authRepo).save(captor.capture());
        AuthorizationRequest saved = captor.getValue();
        assertThat(saved.getAuthorizationType()).isEqualTo(AuthorizationType.EQUIPMENT_REQUEST);
        assertThat(saved.getEquipmentType()).isEqualTo("Laptop");
        assertThat(saved.getStartDate()).isEqualTo(body.getStartDate());
        assertThat(saved.getEndDate()).isEqualTo(body.getEndDate());
        assertThat(saved.getReason()).isEqualTo("Need it for office work");
        assertThat(result).containsEntry("equipmentType", "Laptop");
    }

    @Test
    void equipmentRequest_rejectsMissingEquipmentType() {
        CreateAuthorizationRequestDto body = equipmentRequest();
        body.setEquipmentType(" ");

        assertBadRequest(body, "Equipment type is required.");
    }

    @Test
    void equipmentRequest_rejectsMissingReason() {
        CreateAuthorizationRequestDto body = equipmentRequest();
        body.setReason(" ");

        assertBadRequest(body, "Reason is required.");
    }

    @Test
    void equipmentRequest_rejectsMissingStartDate() {
        CreateAuthorizationRequestDto body = equipmentRequest();
        body.setStartDate(null);

        assertBadRequest(body, "Needed from date is required.");
    }

    @Test
    void equipmentRequest_rejectsPastStartDate() {
        CreateAuthorizationRequestDto body = equipmentRequest();
        body.setStartDate(LocalDate.now().minusDays(1));

        assertBadRequest(body, "Needed from date cannot be in the past.");
    }

    @Test
    void equipmentRequest_rejectsEndDateBeforeStartDate() {
        CreateAuthorizationRequestDto body = equipmentRequest();
        body.setStartDate(LocalDate.now().plusDays(3));
        body.setEndDate(LocalDate.now().plusDays(2));

        assertBadRequest(body, "Expected return date must be on or after needed from date.");
    }

    @Test
    void businessTripCreationRejectsDisabledType() {
        CreateAuthorizationRequestDto body = equipmentRequest();
        body.setAuthorizationType(AuthorizationType.BUSINESS_TRIP);

        assertBadRequest(body, "This authorization type is no longer available for new requests.");
    }

    @Test
    void trainingCreationRejectsDisabledType() {
        CreateAuthorizationRequestDto body = equipmentRequest();
        body.setAuthorizationType(AuthorizationType.TRAINING);

        assertBadRequest(body, "This authorization type is no longer available for new requests.");
    }

    @Test
    void legacyAuthorizationTypesStillExistForBackwardCompatibility() {
        assertThat(AuthorizationType.values()).contains(
                AuthorizationType.BUSINESS_TRIP,
                AuthorizationType.TRAINING,
                AuthorizationType.TIME_PERMISSION,
                AuthorizationType.EQUIPMENT_REQUEST
        );
    }

    private void assertBadRequest(CreateAuthorizationRequestDto body, String message) {
        assertThatThrownBy(() -> service.createAuthRequest(jwt, body))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(message);
        verifyNoInteractions(authRepo);
    }

    private CreateAuthorizationRequestDto equipmentRequest() {
        CreateAuthorizationRequestDto body = new CreateAuthorizationRequestDto();
        body.setAuthorizationType(AuthorizationType.EQUIPMENT_REQUEST);
        body.setEquipmentType("Laptop");
        body.setStartDate(LocalDate.now().plusDays(1));
        body.setEndDate(LocalDate.now().plusDays(7));
        body.setReason("Need it for office work");
        return body;
    }

    private User user() {
        Person person = new Person();
        person.setFirstName("Amina");
        person.setLastName("Ben Ali");
        person.setEmail("amina@example.test");

        User user = new User();
        user.setId(1L);
        user.setUsername("amina");
        user.setKeycloakId("kc-amina");
        user.setPerson(person);
        return user;
    }
}
