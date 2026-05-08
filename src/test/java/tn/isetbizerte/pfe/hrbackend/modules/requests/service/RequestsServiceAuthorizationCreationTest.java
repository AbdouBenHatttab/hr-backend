package tn.isetbizerte.pfe.hrbackend.modules.requests.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.Jwt;
import tn.isetbizerte.pfe.hrbackend.common.enums.AuthorizationType;
import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveStatus;
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RequestsServiceAuthorizationCreationTest {

    private AuthorizationRequestRepository authRepo;
    private AuthenticatedUserResolver authenticatedUserResolver;
    private WorkingDayService workingDayService;
    private LeaveRequestRepository leaveRequestRepository;
    private RequestsService service;
    private Jwt jwt;

    private LocalDate workingMonday;

    @BeforeEach
    void setUp() {
        authRepo = mock(AuthorizationRequestRepository.class);
        authenticatedUserResolver = mock(AuthenticatedUserResolver.class);
        workingDayService = mock(WorkingDayService.class);
        leaveRequestRepository = mock(LeaveRequestRepository.class);
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
                leaveRequestRepository,
                workingDayService
        );
        jwt = mock(Jwt.class);
        when(authenticatedUserResolver.require(jwt)).thenReturn(user());

        // Defaults — non-holiday, no leave overlap
        workingMonday = nextMonday();
        when(workingDayService.isPublicHoliday(any())).thenReturn(false);
        when(leaveRequestRepository.findByUserAndDateRangeAndStatusIn(any(), any(), any(), any()))
                .thenReturn(List.of());
    }

    // =========================================================================
    // EQUIPMENT_REQUEST tests (existing — unchanged behavior)
    // =========================================================================

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

    // =========================================================================
    // TIME_PERMISSION tests — official createAuthRequest now uses the same
    // working-time rule as validate-draft (morning, lunch blocked, afternoon).
    // =========================================================================

    @Test
    void timePermission_acceptsMorningSession_08_to_12() {
        CreateAuthorizationRequestDto body = timePermissionRequest(
                workingMonday, LocalTime.of(8, 0), LocalTime.of(12, 0));

        service.createAuthRequest(jwt, body);

        ArgumentCaptor<AuthorizationRequest> captor = ArgumentCaptor.forClass(AuthorizationRequest.class);
        verify(authRepo).save(captor.capture());
        AuthorizationRequest saved = captor.getValue();
        assertThat(saved.getAuthorizationType()).isEqualTo(AuthorizationType.TIME_PERMISSION);
        assertThat(saved.getAbsenceDate()).isEqualTo(workingMonday);
        assertThat(saved.getFromTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(saved.getToTime()).isEqualTo(LocalTime.of(12, 0));
    }

    @Test
    void timePermission_acceptsAfternoonSession_13_to_17() {
        CreateAuthorizationRequestDto body = timePermissionRequest(
                workingMonday, LocalTime.of(13, 0), LocalTime.of(17, 0));

        service.createAuthRequest(jwt, body);

        ArgumentCaptor<AuthorizationRequest> captor = ArgumentCaptor.forClass(AuthorizationRequest.class);
        verify(authRepo).save(captor.capture());
        AuthorizationRequest saved = captor.getValue();
        assertThat(saved.getFromTime()).isEqualTo(LocalTime.of(13, 0));
        assertThat(saved.getToTime()).isEqualTo(LocalTime.of(17, 0));
    }

    @Test
    void timePermission_acceptsMidMorningSlice() {
        CreateAuthorizationRequestDto body = timePermissionRequest(
                workingMonday, LocalTime.of(9, 30), LocalTime.of(11, 0));

        service.createAuthRequest(jwt, body);

        verify(authRepo).save(any(AuthorizationRequest.class));
    }

    @Test
    void timePermission_blocksFromTimeDuringLunch() {
        // 12:30 is inside the 12:00–13:00 lunch break
        CreateAuthorizationRequestDto body = timePermissionRequest(
                workingMonday, LocalTime.of(12, 30), LocalTime.of(14, 0));

        assertThatThrownBy(() -> service.createAuthRequest(jwt, body))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("12:30")
                .hasMessageContaining("outside working windows");
        verifyNoInteractions(authRepo);
    }

    @Test
    void timePermission_blocksToTimeDuringLunch() {
        // 12:30 is inside the 12:00–13:00 lunch break
        CreateAuthorizationRequestDto body = timePermissionRequest(
                workingMonday, LocalTime.of(10, 0), LocalTime.of(12, 30));

        assertThatThrownBy(() -> service.createAuthRequest(jwt, body))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("12:30")
                .hasMessageContaining("outside working windows");
        verifyNoInteractions(authRepo);
    }

    @Test
    void timePermission_blocksRangeSpanningLunch() {
        // 10:00–14:00 starts in morning, ends in afternoon — spans lunch
        CreateAuthorizationRequestDto body = timePermissionRequest(
                workingMonday, LocalTime.of(10, 0), LocalTime.of(14, 0));

        assertThatThrownBy(() -> service.createAuthRequest(jwt, body))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("lunch break");
        verifyNoInteractions(authRepo);
    }

    @Test
    void timePermission_blocksFromTimeBeforeWorkStart() {
        CreateAuthorizationRequestDto body = timePermissionRequest(
                workingMonday, LocalTime.of(7, 0), LocalTime.of(9, 0));

        assertThatThrownBy(() -> service.createAuthRequest(jwt, body))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("outside working windows");
        verifyNoInteractions(authRepo);
    }

    @Test
    void timePermission_blocksToTimeAfterWorkEnd() {
        CreateAuthorizationRequestDto body = timePermissionRequest(
                workingMonday, LocalTime.of(14, 0), LocalTime.of(18, 0));

        assertThatThrownBy(() -> service.createAuthRequest(jwt, body))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("outside working windows");
        verifyNoInteractions(authRepo);
    }

    @Test
    void timePermission_blocksWeekend() {
        LocalDate saturday = nextWeekend(DayOfWeek.SATURDAY);
        CreateAuthorizationRequestDto body = timePermissionRequest(
                saturday, LocalTime.of(9, 0), LocalTime.of(11, 0));

        assertThatThrownBy(() -> service.createAuthRequest(jwt, body))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Weekends are not allowed");
        verifyNoInteractions(authRepo);
    }

    @Test
    void timePermission_blocksPublicHoliday() {
        when(workingDayService.isPublicHoliday(workingMonday)).thenReturn(true);
        CreateAuthorizationRequestDto body = timePermissionRequest(
                workingMonday, LocalTime.of(9, 0), LocalTime.of(11, 0));

        assertThatThrownBy(() -> service.createAuthRequest(jwt, body))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("public holiday");
        verifyNoInteractions(authRepo);
    }

    @Test
    void timePermission_blocksToTimeBeforeFromTime() {
        CreateAuthorizationRequestDto body = timePermissionRequest(
                workingMonday, LocalTime.of(11, 0), LocalTime.of(9, 0));

        assertThatThrownBy(() -> service.createAuthRequest(jwt, body))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("To time must be after from time");
        verifyNoInteractions(authRepo);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

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

    private CreateAuthorizationRequestDto timePermissionRequest(
            LocalDate date, LocalTime from, LocalTime to) {
        CreateAuthorizationRequestDto body = new CreateAuthorizationRequestDto();
        body.setAuthorizationType(AuthorizationType.TIME_PERMISSION);
        body.setAbsenceDate(date);
        body.setFromTime(from);
        body.setToTime(to);
        body.setReason("Personal errand");
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

    private LocalDate nextMonday() {
        LocalDate d = LocalDate.now().plusDays(1);
        while (d.getDayOfWeek() != DayOfWeek.MONDAY) {
            d = d.plusDays(1);
        }
        return d;
    }

    private LocalDate nextWeekend(DayOfWeek targetDay) {
        LocalDate d = LocalDate.now().plusDays(1);
        while (d.getDayOfWeek() != targetDay) {
            d = d.plusDays(1);
        }
        return d;
    }
}
