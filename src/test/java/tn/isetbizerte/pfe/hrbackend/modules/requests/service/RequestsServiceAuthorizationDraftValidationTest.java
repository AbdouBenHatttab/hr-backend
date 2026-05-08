package tn.isetbizerte.pfe.hrbackend.modules.requests.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import tn.isetbizerte.pfe.hrbackend.common.enums.AuthorizationType;
import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveStatus;
import tn.isetbizerte.pfe.hrbackend.infrastructure.kafka.producer.RequestEventProducer;
import tn.isetbizerte.pfe.hrbackend.infrastructure.storage.DocumentAttachmentStorageService;
import tn.isetbizerte.pfe.hrbackend.modules.calendar.service.WorkingDayService;
import tn.isetbizerte.pfe.hrbackend.modules.employee.entity.LeaveRequest;
import tn.isetbizerte.pfe.hrbackend.modules.employee.repository.LeaveRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.history.service.RequestHistoryService;
import tn.isetbizerte.pfe.hrbackend.modules.requests.dto.ValidateAuthorizationDraftRequestDto;
import tn.isetbizerte.pfe.hrbackend.modules.requests.dto.ValidateAuthorizationDraftResponseDto;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RequestsService.validateAuthorizationDraft.
 *
 * No Spring context is loaded. All collaborators are Mockito mocks.
 * No DB, no Kafka, no history — pure business-rule coverage.
 */
class RequestsServiceAuthorizationDraftValidationTest {

    private WorkingDayService workingDayService;
    private LeaveRequestRepository leaveRequestRepository;
    private AuthenticatedUserResolver authenticatedUserResolver;
    private AuthorizationRequestRepository authRepo;

    private RequestsService service;
    private Jwt jwt;
    private User user;

    // A working Monday that is not a public holiday
    private LocalDate workingMonday;

    @BeforeEach
    void setUp() {
        workingDayService        = mock(WorkingDayService.class);
        leaveRequestRepository   = mock(LeaveRequestRepository.class);
        authenticatedUserResolver = mock(AuthenticatedUserResolver.class);
        authRepo                 = mock(AuthorizationRequestRepository.class);

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

        jwt  = mock(Jwt.class);
        user = buildUser();

        when(authenticatedUserResolver.require(jwt)).thenReturn(user);

        // Find the next Monday from today (safe working day assumption for tests)
        workingMonday = nextMonday();
        // Default: workingMonday is not a public holiday
        when(workingDayService.isPublicHoliday(workingMonday)).thenReturn(false);
        // Default: no leave overlap
        when(leaveRequestRepository.findByUserAndDateRangeAndStatusIn(
                eq(user), eq(workingMonday), eq(workingMonday), any()))
                .thenReturn(List.of());
    }

    // =========================================================================
    // TIME_PERMISSION — valid case
    // =========================================================================

    @Test
    void timePermission_valid_returnsValidTrue() {
        var req = timePermissionRequest(workingMonday, LocalTime.of(9, 0), LocalTime.of(11, 0));

        ValidateAuthorizationDraftResponseDto result = service.validateAuthorizationDraft(req, jwt);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getAuthorizationType()).isEqualTo(AuthorizationType.TIME_PERMISSION);
        assertThat(result.getMessage()).isNotBlank();
    }

    // =========================================================================
    // TIME_PERMISSION — missing required fields
    // =========================================================================

    @Test
    void timePermission_missingAbsenceDate_returnsError() {
        var req = timePermissionRequest(null, LocalTime.of(9, 0), LocalTime.of(11, 0));

        var result = service.validateAuthorizationDraft(req, jwt);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("absenceDate") || e.contains("date"));
    }

    @Test
    void timePermission_missingFromTime_returnsError() {
        var req = timePermissionRequest(workingMonday, null, LocalTime.of(11, 0));

        var result = service.validateAuthorizationDraft(req, jwt);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("fromTime"));
    }

    @Test
    void timePermission_missingToTime_returnsError() {
        var req = timePermissionRequest(workingMonday, LocalTime.of(9, 0), null);

        var result = service.validateAuthorizationDraft(req, jwt);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("toTime"));
    }

    // =========================================================================
    // TIME_PERMISSION — time-range order
    // =========================================================================

    @Test
    void timePermission_toTimeBeforeFromTime_returnsError() {
        var req = timePermissionRequest(workingMonday, LocalTime.of(11, 0), LocalTime.of(9, 0));

        var result = service.validateAuthorizationDraft(req, jwt);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("toTime") || e.contains("after"));
    }

    @Test
    void timePermission_toTimeEqualFromTime_returnsError() {
        var req = timePermissionRequest(workingMonday, LocalTime.of(10, 0), LocalTime.of(10, 0));

        var result = service.validateAuthorizationDraft(req, jwt);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("toTime") || e.contains("after"));
    }

    // =========================================================================
    // TIME_PERMISSION — weekend
    // =========================================================================

    @Test
    void timePermission_saturday_returnsError() {
        LocalDate saturday = nextWeekend(DayOfWeek.SATURDAY);
        when(workingDayService.isPublicHoliday(saturday)).thenReturn(false);
        var req = timePermissionRequest(saturday, LocalTime.of(9, 0), LocalTime.of(11, 0));

        var result = service.validateAuthorizationDraft(req, jwt);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.toLowerCase().contains("weekend")
                || e.toLowerCase().contains("working day"));
    }

    @Test
    void timePermission_sunday_returnsError() {
        LocalDate sunday = nextWeekend(DayOfWeek.SUNDAY);
        when(workingDayService.isPublicHoliday(sunday)).thenReturn(false);
        var req = timePermissionRequest(sunday, LocalTime.of(9, 0), LocalTime.of(11, 0));

        var result = service.validateAuthorizationDraft(req, jwt);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.toLowerCase().contains("weekend")
                || e.toLowerCase().contains("working day"));
    }

    // =========================================================================
    // TIME_PERMISSION — public holiday
    // =========================================================================

    @Test
    void timePermission_publicHoliday_returnsError() {
        when(workingDayService.isPublicHoliday(workingMonday)).thenReturn(true);
        var req = timePermissionRequest(workingMonday, LocalTime.of(9, 0), LocalTime.of(11, 0));

        var result = service.validateAuthorizationDraft(req, jwt);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.toLowerCase().contains("holiday"));
    }

    // =========================================================================
    // TIME_PERMISSION — leave overlap
    // =========================================================================

    @Test
    void timePermission_overlapsApprovedLeave_returnsError() {
        LeaveRequest leave = buildLeave(LeaveStatus.APPROVED);
        when(leaveRequestRepository.findByUserAndDateRangeAndStatusIn(
                eq(user), eq(workingMonday), eq(workingMonday), any()))
                .thenReturn(List.of(leave));

        var req = timePermissionRequest(workingMonday, LocalTime.of(9, 0), LocalTime.of(11, 0));

        var result = service.validateAuthorizationDraft(req, jwt);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.toLowerCase().contains("approved leave"));
    }

    @Test
    void timePermission_overlapsPendingLeave_returnsError() {
        LeaveRequest leave = buildLeave(LeaveStatus.PENDING);
        when(leaveRequestRepository.findByUserAndDateRangeAndStatusIn(
                eq(user), eq(workingMonday), eq(workingMonday), any()))
                .thenReturn(List.of(leave));

        var req = timePermissionRequest(workingMonday, LocalTime.of(9, 0), LocalTime.of(11, 0));

        var result = service.validateAuthorizationDraft(req, jwt);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.toLowerCase().contains("pending leave"));
    }

    // =========================================================================
    // TIME_PERMISSION — outside working hours
    // =========================================================================

    @Test
    void timePermission_fromTimeBeforeWorkStart_returnsError() {
        // 07:00 is before 08:00
        var req = timePermissionRequest(workingMonday, LocalTime.of(7, 0), LocalTime.of(9, 0));

        var result = service.validateAuthorizationDraft(req, jwt);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("fromTime") || e.contains("outside"));
    }

    @Test
    void timePermission_toTimeAfterWorkEnd_returnsError() {
        // 18:00 is after 17:00
        var req = timePermissionRequest(workingMonday, LocalTime.of(14, 0), LocalTime.of(18, 0));

        var result = service.validateAuthorizationDraft(req, jwt);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("toTime") || e.contains("outside"));
    }

    // =========================================================================
    // TIME_PERMISSION — lunch break (new stricter rule)
    // =========================================================================

    @Test
    void timePermission_fromTimeDuringLunch_returnsError() {
        // 12:30 is inside 12:00–13:00 lunch break
        var req = timePermissionRequest(workingMonday, LocalTime.of(12, 30), LocalTime.of(14, 0));

        var result = service.validateAuthorizationDraft(req, jwt);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e ->
                e.contains("12:30") || e.contains("lunch") || e.contains("outside"));
    }

    @Test
    void timePermission_rangeSpansLunchBreak_returnsError() {
        // 10:00–14:00 spans 12:00–13:00
        var req = timePermissionRequest(workingMonday, LocalTime.of(10, 0), LocalTime.of(14, 0));

        var result = service.validateAuthorizationDraft(req, jwt);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e ->
                e.toLowerCase().contains("lunch") || e.toLowerCase().contains("spans"));
    }

    @Test
    void timePermission_morningSessionExactBoundary_isValid() {
        // 08:00–12:00 is valid morning session (toTime == LUNCH_START is allowed as the end boundary)
        var req = timePermissionRequest(workingMonday, LocalTime.of(8, 0), LocalTime.of(12, 0));

        var result = service.validateAuthorizationDraft(req, jwt);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void timePermission_afternoonSessionExactBoundary_isValid() {
        // 13:00–17:00 is valid afternoon session
        var req = timePermissionRequest(workingMonday, LocalTime.of(13, 0), LocalTime.of(17, 0));

        var result = service.validateAuthorizationDraft(req, jwt);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
    }

    // =========================================================================
    // Blocked types — BUSINESS_TRIP and TRAINING
    // =========================================================================

    @Test
    void businessTrip_isBlocked() {
        var req = new ValidateAuthorizationDraftRequestDto();
        req.setAuthorizationType(AuthorizationType.BUSINESS_TRIP);
        req.setAbsenceDate(workingMonday);

        var result = service.validateAuthorizationDraft(req, jwt);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e ->
                e.contains("BUSINESS_TRIP") || e.toLowerCase().contains("no longer available"));
        verifyNoInteractions(authRepo);
    }

    @Test
    void training_isBlocked() {
        var req = new ValidateAuthorizationDraftRequestDto();
        req.setAuthorizationType(AuthorizationType.TRAINING);

        var result = service.validateAuthorizationDraft(req, jwt);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e ->
                e.contains("TRAINING") || e.toLowerCase().contains("no longer available"));
        verifyNoInteractions(authRepo);
    }

    // =========================================================================
    // EQUIPMENT_REQUEST — valid case
    // =========================================================================

    @Test
    void equipmentRequest_valid_returnsValidTrue() {
        var req = equipmentRequest("Laptop", LocalDate.now().plusDays(1), LocalDate.now().plusDays(7));

        var result = service.validateAuthorizationDraft(req, jwt);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getAuthorizationType()).isEqualTo(AuthorizationType.EQUIPMENT_REQUEST);
    }

    @Test
    void equipmentRequest_validWithoutEndDate_returnsValidTrue() {
        // endDate is optional
        var req = equipmentRequest("Monitor", LocalDate.now().plusDays(1), null);

        var result = service.validateAuthorizationDraft(req, jwt);

        assertThat(result.isValid()).isTrue();
    }

    // =========================================================================
    // EQUIPMENT_REQUEST — missing required fields
    // =========================================================================

    @Test
    void equipmentRequest_missingEquipmentType_returnsError() {
        var req = equipmentRequest(null, LocalDate.now().plusDays(1), null);

        var result = service.validateAuthorizationDraft(req, jwt);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.toLowerCase().contains("equipment type"));
    }

    @Test
    void equipmentRequest_blankEquipmentType_returnsError() {
        var req = equipmentRequest("   ", LocalDate.now().plusDays(1), null);

        var result = service.validateAuthorizationDraft(req, jwt);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.toLowerCase().contains("equipment type"));
    }

    @Test
    void equipmentRequest_missingReason_returnsError() {
        ValidateAuthorizationDraftRequestDto req = equipmentRequest("Laptop", LocalDate.now().plusDays(1), null);
        req.setReason(null);

        var result = service.validateAuthorizationDraft(req, jwt);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.toLowerCase().contains("reason"));
    }

    @Test
    void equipmentRequest_missingStartDate_returnsError() {
        var req = equipmentRequest("Laptop", null, null);

        var result = service.validateAuthorizationDraft(req, jwt);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e ->
                e.toLowerCase().contains("startdate") || e.toLowerCase().contains("needed-from"));
    }

    // =========================================================================
    // EQUIPMENT_REQUEST — invalid date range
    // =========================================================================

    @Test
    void equipmentRequest_endDateBeforeStartDate_returnsError() {
        var req = equipmentRequest("Tablet",
                LocalDate.now().plusDays(5),
                LocalDate.now().plusDays(2));

        var result = service.validateAuthorizationDraft(req, jwt);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e ->
                e.toLowerCase().contains("return date") || e.toLowerCase().contains("after"));
    }

    // =========================================================================
    // No DB / Kafka / history side-effects
    // =========================================================================

    @Test
    void validate_neverSavesToDb() {
        var req = timePermissionRequest(workingMonday, LocalTime.of(9, 0), LocalTime.of(11, 0));

        service.validateAuthorizationDraft(req, jwt);

        verifyNoInteractions(authRepo);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private ValidateAuthorizationDraftRequestDto timePermissionRequest(
            LocalDate date, LocalTime from, LocalTime to) {
        var req = new ValidateAuthorizationDraftRequestDto();
        req.setAuthorizationType(AuthorizationType.TIME_PERMISSION);
        req.setAbsenceDate(date);
        req.setFromTime(from);
        req.setToTime(to);
        return req;
    }

    private ValidateAuthorizationDraftRequestDto equipmentRequest(
            String type, LocalDate start, LocalDate end) {
        var req = new ValidateAuthorizationDraftRequestDto();
        req.setAuthorizationType(AuthorizationType.EQUIPMENT_REQUEST);
        req.setEquipmentType(type);
        req.setStartDate(start);
        req.setEndDate(end);
        req.setReason("Work from home project");
        return req;
    }

    private User buildUser() {
        Person person = new Person();
        person.setFirstName("Amina");
        person.setLastName("Ben Ali");
        person.setEmail("amina@example.test");

        User u = new User();
        u.setId(1L);
        u.setUsername("amina");
        u.setKeycloakId("kc-amina");
        u.setPerson(person);
        return u;
    }

    private LeaveRequest buildLeave(LeaveStatus status) {
        LeaveRequest leave = new LeaveRequest();
        leave.setStatus(status);
        leave.setUser(user);
        return leave;
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
