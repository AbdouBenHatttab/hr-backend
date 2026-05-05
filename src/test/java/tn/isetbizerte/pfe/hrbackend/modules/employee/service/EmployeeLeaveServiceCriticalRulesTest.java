package tn.isetbizerte.pfe.hrbackend.modules.employee.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import tn.isetbizerte.pfe.hrbackend.common.enums.ApprovalDecision;
import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveStatus;
import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveType;
import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;
import tn.isetbizerte.pfe.hrbackend.common.exception.BadRequestException;
import tn.isetbizerte.pfe.hrbackend.infrastructure.email.HREmailService;
import tn.isetbizerte.pfe.hrbackend.infrastructure.kafka.producer.RequestEventProducer;
import tn.isetbizerte.pfe.hrbackend.modules.calendar.service.WorkingDayService;
import tn.isetbizerte.pfe.hrbackend.modules.employee.dto.CreateLeaveRequestDto;
import tn.isetbizerte.pfe.hrbackend.modules.employee.dto.LeaveRequestResponseDto;
import tn.isetbizerte.pfe.hrbackend.modules.employee.entity.LeaveRequest;
import tn.isetbizerte.pfe.hrbackend.modules.employee.repository.LeaveRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.history.service.RequestHistoryService;
import tn.isetbizerte.pfe.hrbackend.modules.team.entity.Team;
import tn.isetbizerte.pfe.hrbackend.modules.team.repository.TeamRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.Person;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.PersonRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeLeaveServiceCriticalRulesTest {

    @Mock private LeaveRequestRepository leaveRequestRepository;
    @Mock private UserRepository userRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private PersonRepository personRepository;
    @Mock private LeaveScoreEngine leaveScoreEngine;
    @Mock private HREmailService emailService;
    @Mock private RequestEventProducer requestEventProducer;
    @Mock private RequestHistoryService historyService;
    @Mock private LeaveBalanceService leaveBalanceService;
    @Mock private WorkingDayService workingDayService;

    private EmployeeLeaveService service;
    private User employee;

    @BeforeEach
    void setUp() {
        // Wire a real LeaveValidationService backed by the same mocks so that
        // all existing validation tests continue to exercise real rule logic,
        // not a mock. This preserves the full test coverage intent.
        LeaveValidationService leaveValidationService = new LeaveValidationService(
                workingDayService,
                leaveBalanceService,
                leaveRequestRepository
        );

        service = new EmployeeLeaveService(
                leaveRequestRepository,
                userRepository,
                teamRepository,
                personRepository,
                leaveScoreEngine,
                emailService,
                requestEventProducer,
                historyService,
                leaveBalanceService,
                workingDayService,
                leaveValidationService
        );
        employee = new User("kc-employee", "employee");
        employee.setId(10L);
        employee.setActive(true);
        employee.setRole(TypeRole.EMPLOYEE);
        employee.setTeam(team(1L, teamLeader("kc-default-leader")));
        lenient().when(userRepository.findByUsername("employee")).thenReturn(Optional.of(employee));
    }

    @Test
    void createLeaveRequest_rejectsEmployeeWithoutTeam() {
        employee.setTeam(null);
        LocalDate start = LocalDate.now().plusDays(10);

        assertThatThrownBy(() -> service.createLeaveRequest("employee", dto(LeaveType.SICK, start, 1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("You must be assigned to a team before submitting a leave request.");

        verifyNoInteractions(workingDayService);
        verifyNoInteractions(leaveBalanceService);
        verify(leaveRequestRepository, never()).save(any());
    }

    @Test
    void createLeaveRequest_rejectsEmployeeWhenTeamHasNoLeader() {
        Team team = new Team();
        team.setId(2L);
        team.setName("No Leader Team");
        employee.setTeam(team);
        LocalDate start = LocalDate.now().plusDays(10);

        assertThatThrownBy(() -> service.createLeaveRequest("employee", dto(LeaveType.SICK, start, 1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Your team has no Team Leader assigned. Ask HR to complete team setup before submitting leave.");

        verifyNoInteractions(workingDayService);
        verifyNoInteractions(leaveBalanceService);
        verify(leaveRequestRepository, never()).save(any());
    }

    @Test
    void createLeaveRequest_rejectsOverlappingPendingOrApprovedLeave() {
        LocalDate start = LocalDate.now().plusDays(10);
        when(workingDayService.isWorkingDay(start)).thenReturn(true);
        when(workingDayService.countWorkingDays(start, start)).thenReturn(1);
        LeaveRequest existing = new LeaveRequest();
        existing.setStatus(LeaveStatus.PENDING);
        existing.setStartDate(start.minusDays(1));
        existing.setEndDate(start.plusDays(1));

        when(leaveRequestRepository.findByUserAndDateRangeAndStatusIn(
                eq(employee), eq(start), eq(start), anyList()
        )).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.createLeaveRequest("employee", dto(LeaveType.SICK, start, 1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("This leave request overlaps with an existing pending leave request from %s to %s.",
                        existing.getStartDate(), existing.getEndDate());

        verifyNoInteractions(leaveBalanceService);
        verify(leaveRequestRepository, never()).save(any());
    }

    @Test
    void createLeaveRequest_rejectsAnnualLeaveMoreThanSixMonthsInAdvance() {
        LocalDate start = LocalDate.now().plusMonths(6).plusDays(1);
        when(workingDayService.isWorkingDay(start)).thenReturn(true);
        when(workingDayService.countWorkingDays(start, start)).thenReturn(1);
        when(leaveRequestRepository.findByUserAndDateRangeAndStatusIn(
                eq(employee), eq(start), eq(start), anyList()
        )).thenReturn(List.of());

        assertThatThrownBy(() -> service.createLeaveRequest("employee", dto(LeaveType.ANNUAL, start, 1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Annual leave cannot be requested more than 6 months in advance.");

        verify(leaveBalanceService, never()).reserveForRequest(any(), any(), any(), anyInt());
        verify(leaveRequestRepository, never()).save(any());
    }

    @Test
    void createLeaveRequest_rejectsAnnualLeaveStartingToday() {
        LocalDate start = LocalDate.now();
        when(workingDayService.isWorkingDay(start)).thenReturn(true);
        when(workingDayService.countWorkingDays(start, start)).thenReturn(1);
        when(leaveRequestRepository.findByUserAndDateRangeAndStatusIn(
                eq(employee), eq(start), eq(start), anyList()
        )).thenReturn(List.of());

        assertThatThrownBy(() -> service.createLeaveRequest("employee", dto(LeaveType.ANNUAL, start, 1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Annual leave must be requested at least 1 day in advance.");

        verify(leaveBalanceService, never()).reserveForRequest(any(), any(), any(), anyInt());
        verify(leaveRequestRepository, never()).save(any());
    }

    @Test
    void createLeaveRequest_rejectsSickLeaveMoreThanSevenDaysInAdvance() {
        LocalDate start = LocalDate.now().plusDays(8);
        when(workingDayService.isWorkingDay(start)).thenReturn(true);
        when(workingDayService.countWorkingDays(start, start)).thenReturn(1);
        when(leaveRequestRepository.findByUserAndDateRangeAndStatusIn(
                eq(employee), eq(start), eq(start), anyList()
        )).thenReturn(List.of());

        assertThatThrownBy(() -> service.createLeaveRequest("employee", dto(LeaveType.SICK, start, 1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Sick leave cannot be requested more than 7 days in advance.");

        verify(leaveBalanceService, never()).reserveForRequest(any(), any(), any(), anyInt());
        verify(leaveRequestRepository, never()).save(any());
    }

    @Test
    void createLeaveRequest_rejectsAnnualLeaveWhenRequestedDaysExceedAvailableBalance() {
        LocalDate start = LocalDate.now().plusDays(20);
        LocalDate end = start.plusDays(4);
        when(leaveRequestRepository.findByUserAndDateRangeAndStatusIn(
                eq(employee), eq(start), eq(end), anyList()
        )).thenReturn(List.of());
        when(workingDayService.isWorkingDay(start)).thenReturn(true);
        when(workingDayService.isWorkingDay(end)).thenReturn(true);
        when(workingDayService.countWorkingDays(start, end)).thenReturn(5);
        when(leaveBalanceService.getAvailableDays(employee, LeaveType.ANNUAL, start)).thenReturn(BigDecimal.valueOf(3));

        assertThatThrownBy(() -> service.createLeaveRequest("employee", dto(LeaveType.ANNUAL, start, 5)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Insufficient annual leave balance. Requested 5 working day(s), but only 3 day(s) are available.");

        verify(leaveBalanceService, never()).reserveForRequest(any(), any(), any(), anyInt());
        verify(leaveRequestRepository, never()).save(any());
    }

    @Test
    void createLeaveRequest_storesWorkingDaysForNormalWeekdays() {
        LocalDate start = LocalDate.now().plusDays(7);
        LocalDate end = start.plusDays(4);
        when(workingDayService.isWorkingDay(start)).thenReturn(true);
        when(workingDayService.isWorkingDay(end)).thenReturn(true);
        when(workingDayService.countWorkingDays(start, end)).thenReturn(5);
        when(leaveRequestRepository.findByUserAndDateRangeAndStatusIn(eq(employee), eq(start), eq(end), anyList()))
                .thenReturn(List.of());
        when(leaveBalanceService.getAvailableDays(employee, LeaveType.ANNUAL, start)).thenReturn(BigDecimal.TEN);
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> {
            LeaveRequest saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        service.createLeaveRequest("employee", dto(LeaveType.ANNUAL, start, 5));

        verify(leaveBalanceService).reserveForRequest(employee, LeaveType.ANNUAL, start, 5);
        verify(leaveRequestRepository).save(argThat(leave -> leave.getNumberOfDays() == 5));
    }

    @Test
    void createLeaveRequest_allowsEmployeeWithTeamAndLeader() {
        LocalDate start = LocalDate.now().plusDays(6);
        when(workingDayService.isWorkingDay(start)).thenReturn(true);
        when(workingDayService.countWorkingDays(start, start)).thenReturn(1);
        when(leaveRequestRepository.findByUserAndDateRangeAndStatusIn(eq(employee), eq(start), eq(start), anyList()))
                .thenReturn(List.of());
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> {
            LeaveRequest saved = invocation.getArgument(0);
            saved.setId(6L);
            return saved;
        });

        LeaveRequestResponseDto response = service.createLeaveRequest("employee", dto(LeaveType.SICK, start, 1));

        assertThat(response.getTeamLeaderDecision()).isEqualTo(ApprovalDecision.PENDING);
        assertThat(response.getApprovalStage()).isEqualTo("PENDING_TL");
        verify(leaveBalanceService).reserveForRequest(employee, LeaveType.SICK, start, 1);
        verify(leaveRequestRepository).save(any(LeaveRequest.class));
    }

    @Test
    void createLeaveRequest_teamLeaderOwnLeaveStillBypassesTeamLeaderStep() {
        User leader = teamLeader("kc-leader");
        when(userRepository.findByUsername("leader")).thenReturn(Optional.of(leader));
        LocalDate start = LocalDate.now().plusDays(6);
        when(workingDayService.isWorkingDay(start)).thenReturn(true);
        when(workingDayService.countWorkingDays(start, start)).thenReturn(1);
        when(leaveRequestRepository.findByUserAndDateRangeAndStatusIn(eq(leader), eq(start), eq(start), anyList()))
                .thenReturn(List.of());
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> {
            LeaveRequest saved = invocation.getArgument(0);
            saved.setId(7L);
            return saved;
        });

        LeaveRequestResponseDto response = service.createLeaveRequest("leader", dto(LeaveType.SICK, start, 1));

        assertThat(response.getTeamLeaderDecision()).isEqualTo(ApprovalDecision.APPROVED);
        assertThat(response.getApprovalStage()).isEqualTo("PENDING_HR");
        verify(leaveBalanceService).reserveForRequest(leader, LeaveType.SICK, start, 1);
    }

    @Test
    void createLeaveRequest_storesWorkingDaysWhenRangeSpansWeekend() {
        LocalDate friday = LocalDate.now().plusDays(7);
        while (friday.getDayOfWeek() != java.time.DayOfWeek.FRIDAY) {
            friday = friday.plusDays(1);
        }
        LocalDate tuesday = friday.plusDays(4);
        when(workingDayService.isWorkingDay(friday)).thenReturn(true);
        when(workingDayService.isWorkingDay(tuesday)).thenReturn(true);
        when(workingDayService.countWorkingDays(friday, tuesday)).thenReturn(3);
        when(leaveRequestRepository.findByUserAndDateRangeAndStatusIn(eq(employee), eq(friday), eq(tuesday), anyList()))
                .thenReturn(List.of());
        when(leaveBalanceService.getAvailableDays(employee, LeaveType.ANNUAL, friday)).thenReturn(BigDecimal.TEN);
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> {
            LeaveRequest saved = invocation.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        service.createLeaveRequest("employee", dto(LeaveType.ANNUAL, friday, 5));

        verify(leaveBalanceService).reserveForRequest(employee, LeaveType.ANNUAL, friday, 3);
        verify(leaveRequestRepository).save(argThat(leave -> leave.getNumberOfDays() == 3));
    }

    @Test
    void createLeaveRequest_ignoresClientNumberOfDaysAndStoresAuthoritativeWorkingDays() {
        LocalDate start = LocalDate.now().plusDays(20);
        LocalDate end = start.plusDays(2);
        CreateLeaveRequestDto request = dto(LeaveType.ANNUAL, start, 3);
        request.setNumberOfDays(1);

        when(workingDayService.isWorkingDay(start)).thenReturn(true);
        when(workingDayService.isWorkingDay(end)).thenReturn(true);
        when(workingDayService.countWorkingDays(start, end)).thenReturn(3);
        when(leaveRequestRepository.findByUserAndDateRangeAndStatusIn(eq(employee), eq(start), eq(end), anyList()))
                .thenReturn(List.of());
        when(leaveBalanceService.getAvailableDays(employee, LeaveType.ANNUAL, start)).thenReturn(BigDecimal.TEN);
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> {
            LeaveRequest saved = invocation.getArgument(0);
            saved.setId(4L);
            return saved;
        });

        service.createLeaveRequest("employee", request);

        verify(leaveBalanceService).reserveForRequest(employee, LeaveType.ANNUAL, start, 3);
        verify(leaveRequestRepository).save(argThat(leave -> leave.getNumberOfDays() == 3));
    }

    @Test
    void createLeaveRequest_storesWorkingDaysWhenRangeSpansTunisianHoliday() {
        LocalDate start = LocalDate.now().plusDays(20);
        LocalDate end = start.plusDays(2);
        when(workingDayService.isWorkingDay(start)).thenReturn(true);
        when(workingDayService.isWorkingDay(end)).thenReturn(true);
        when(workingDayService.countWorkingDays(start, end)).thenReturn(2);
        when(leaveRequestRepository.findByUserAndDateRangeAndStatusIn(eq(employee), eq(start), eq(end), anyList()))
                .thenReturn(List.of());
        when(leaveBalanceService.getAvailableDays(employee, LeaveType.ANNUAL, start)).thenReturn(BigDecimal.TEN);
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> {
            LeaveRequest saved = invocation.getArgument(0);
            saved.setId(3L);
            return saved;
        });

        service.createLeaveRequest("employee", dto(LeaveType.ANNUAL, start, 3));

        verify(leaveBalanceService).reserveForRequest(employee, LeaveType.ANNUAL, start, 2);
        verify(leaveRequestRepository).save(argThat(leave -> leave.getNumberOfDays() == 2));
    }

    @Test
    void cancelMyLeaveRequest_allowsEmployeeCancellationWhilePendingTeamLeaderReview() {
        LeaveRequest leave = new LeaveRequest();
        leave.setId(50L);
        leave.setUser(employee);
        leave.setStatus(LeaveStatus.PENDING);
        leave.setTeamLeaderDecision(ApprovalDecision.PENDING);
        leave.setHrDecision(ApprovalDecision.PENDING);
        leave.setRequestDate(LocalDateTime.now());

        when(leaveRequestRepository.findById(50L)).thenReturn(Optional.of(leave));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.cancelMyLeaveRequest(50L, employee.getKeycloakId());

        verify(leaveBalanceService).releaseReserved(leave);
        verify(leaveRequestRepository).save(argThat(saved -> saved.getStatus() == LeaveStatus.CANCELLED_BY_EMPLOYEE));
    }

    @Test
    void cancelMyLeaveRequest_rejectsCancellationByNonOwner() {
        LeaveRequest leave = pendingLeave(53L, employee);
        when(leaveRequestRepository.findById(53L)).thenReturn(Optional.of(leave));

        assertThatThrownBy(() -> service.cancelMyLeaveRequest(53L, "kc-other"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Only the owner can cancel this leave request.");

        verify(leaveBalanceService, never()).releaseReserved(any());
        verify(leaveRequestRepository, never()).save(any());
    }

    @Test
    void cancelMyLeaveRequest_rejectsEmployeeCancellationWhilePendingHrReview() {
        LeaveRequest leave = new LeaveRequest();
        leave.setId(52L);
        leave.setUser(employee);
        leave.setStatus(LeaveStatus.PENDING);
        leave.setTeamLeaderDecision(ApprovalDecision.APPROVED);
        leave.setHrDecision(ApprovalDecision.PENDING);
        leave.setRequestDate(LocalDateTime.now());

        when(leaveRequestRepository.findById(52L)).thenReturn(Optional.of(leave));

        assertThatThrownBy(() -> service.cancelMyLeaveRequest(52L, employee.getKeycloakId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Only leave requests still pending approval can be canceled by the employee.");

        verify(leaveBalanceService, never()).releaseReserved(any());
        verify(leaveRequestRepository, never()).save(any());
    }

    @Test
    void cancelMyLeaveRequest_stillCancelsWhenRequestEventPublishFails() {
        LeaveRequest leave = pendingLeave(54L, employee);

        when(leaveRequestRepository.findById(54L)).thenReturn(Optional.of(leave));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new RuntimeException("outbox unavailable")).when(requestEventProducer).publish(any());

        LeaveRequestResponseDto response = service.cancelMyLeaveRequest(54L, employee.getKeycloakId());

        assertThat(response.getStatus()).isEqualTo(LeaveStatus.CANCELLED_BY_EMPLOYEE);
        assertThat(response.getApprovalStage()).isEqualTo("CANCELLED_BY_EMPLOYEE");
        verify(leaveBalanceService).releaseReserved(leave);
        verify(leaveRequestRepository).save(argThat(saved -> saved.getStatus() == LeaveStatus.CANCELLED_BY_EMPLOYEE));
    }

    @Test
    void cancelMyLeaveRequest_rejectsEmployeeCancellationAfterTeamLeaderApproval() {
        LeaveRequest leave = new LeaveRequest();
        leave.setId(51L);
        leave.setUser(employee);
        leave.setStatus(LeaveStatus.APPROVED);
        leave.setTeamLeaderDecision(ApprovalDecision.APPROVED);
        leave.setHrDecision(ApprovalDecision.APPROVED);
        leave.setRequestDate(LocalDateTime.now());

        when(leaveRequestRepository.findById(51L)).thenReturn(Optional.of(leave));

        assertThatThrownBy(() -> service.cancelMyLeaveRequest(51L, employee.getKeycloakId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Only leave requests still pending approval can be canceled by the employee.");

        verify(leaveBalanceService, never()).releaseReserved(any());
        verify(leaveRequestRepository, never()).save(any());
    }

    @Test
    void teamLeaderApprove_movesRequestToPendingHr() {
        User leader = teamLeader("kc-leader");
        Team team = team(100L, leader);
        employee.setTeam(team);
        LeaveRequest leave = pendingLeave(70L, employee);

        when(leaveRequestRepository.findById(70L)).thenReturn(Optional.of(leave));
        when(teamRepository.findByTeamLeaderKeycloakId("kc-leader")).thenReturn(Optional.of(team));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LeaveRequestResponseDto response = service.teamLeaderDecision(70L, true, "kc-leader");

        assertThat(response.getStatus()).isEqualTo(LeaveStatus.PENDING);
        assertThat(response.getApprovalStage()).isEqualTo("PENDING_HR");
        assertThat(response.getTeamLeaderDecision()).isEqualTo(ApprovalDecision.APPROVED);
        assertThat(response.getHrDecision()).isEqualTo(ApprovalDecision.PENDING);
        assertThat(response.getApprovedBy()).isEqualTo("kc-leader");
        verify(leaveBalanceService, never()).releaseReserved(any());
    }

    @Test
    void teamLeaderReject_rejectsAndStoresAuditInfo() {
        User leader = teamLeader("kc-leader");
        Team team = team(101L, leader);
        employee.setTeam(team);
        LeaveRequest leave = pendingLeave(71L, employee);

        when(leaveRequestRepository.findById(71L)).thenReturn(Optional.of(leave));
        when(userRepository.findByKeycloakId("kc-leader")).thenReturn(Optional.of(leader));
        when(teamRepository.findByTeamLeaderKeycloakId("kc-leader")).thenReturn(Optional.of(team));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LeaveRequestResponseDto response = service.teamLeaderDecision(71L, false, "kc-leader", "Coverage is too low.");

        assertThat(response.getStatus()).isEqualTo(LeaveStatus.REJECTED);
        assertThat(response.getApprovalStage()).isEqualTo("REJECTED");
        assertThat(response.getTeamLeaderDecision()).isEqualTo(ApprovalDecision.REJECTED);
        assertThat(response.getHrDecision()).isEqualTo(ApprovalDecision.PENDING);
        assertThat(response.getRejectedBy()).isEqualTo("kc-leader");
        assertThat(response.getRejectedByName()).isEqualTo("Tara Lead");
        assertThat(response.getRejectedByRole()).isEqualTo("TEAM_LEADER");
        assertThat(response.getRejectedAt()).isNotNull();
        assertThat(response.getDecisionReason()).isEqualTo("Coverage is too low.");
        verify(leaveBalanceService).releaseReserved(leave);
    }

    @Test
    void hrApprove_finalizesApprovedLeave() {
        LeaveRequest leave = pendingLeave(72L, employee);
        leave.setTeamLeaderDecision(ApprovalDecision.APPROVED);

        when(leaveRequestRepository.findById(72L)).thenReturn(Optional.of(leave));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LeaveRequestResponseDto response = service.hrDecision(72L, true, null, "kc-hr");

        assertThat(response.getStatus()).isEqualTo(LeaveStatus.APPROVED);
        assertThat(response.getApprovalStage()).isEqualTo("APPROVED");
        assertThat(response.getTeamLeaderDecision()).isEqualTo(ApprovalDecision.APPROVED);
        assertThat(response.getHrDecision()).isEqualTo(ApprovalDecision.APPROVED);
        assertThat(response.getApprovedBy()).isEqualTo("kc-hr");
        assertThat(response.getApprovalDate()).isNotNull();
        assertThat(response.getApprovedAt()).isNotNull();
        verify(leaveBalanceService).consumeReserved(leave);
    }

    @Test
    void hrReject_finalizesRejectedLeaveAndStoresAuditInfo() {
        LeaveRequest leave = pendingLeave(73L, employee);
        leave.setTeamLeaderDecision(ApprovalDecision.APPROVED);
        User hr = hrManager("kc-hr");

        when(leaveRequestRepository.findById(73L)).thenReturn(Optional.of(leave));
        when(userRepository.findByKeycloakId("kc-hr")).thenReturn(Optional.of(hr));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LeaveRequestResponseDto response = service.hrDecision(73L, false, "Policy exception denied.", "kc-hr");

        assertThat(response.getStatus()).isEqualTo(LeaveStatus.REJECTED);
        assertThat(response.getApprovalStage()).isEqualTo("REJECTED");
        assertThat(response.getTeamLeaderDecision()).isEqualTo(ApprovalDecision.APPROVED);
        assertThat(response.getHrDecision()).isEqualTo(ApprovalDecision.REJECTED);
        assertThat(response.getRejectedBy()).isEqualTo("kc-hr");
        assertThat(response.getRejectedByName()).isEqualTo("Hana Manager");
        assertThat(response.getRejectedByRole()).isEqualTo("HR_MANAGER");
        assertThat(response.getRejectedAt()).isNotNull();
        assertThat(response.getDecisionReason()).isEqualTo("Policy exception denied.");
        verify(leaveBalanceService).releaseReserved(leave);
    }

    @Test
    void getMyLeaveRequests_keepsPendingHrRequestsInEmployeePendingWorkflow() {
        LeaveRequest leave = pendingLeave(74L, employee);
        leave.setTeamLeaderDecision(ApprovalDecision.APPROVED);

        when(userRepository.findByKeycloakId(employee.getKeycloakId())).thenReturn(Optional.of(employee));
        when(leaveRequestRepository.findByUser(eq(employee), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(leave)));

        List<LeaveRequestResponseDto> content = service
                .getMyLeaveRequests(employee.getKeycloakId(), org.springframework.data.domain.PageRequest.of(0, 10))
                .getContent();

        assertThat(content).hasSize(1);
        assertThat(content.get(0).getStatus()).isEqualTo(LeaveStatus.PENDING);
        assertThat(content.get(0).getApprovalStage()).isEqualTo("PENDING_HR");
    }

    @Test
    void updateMyLeaveRequest_allowsOwnerWhilePendingTeamLeaderReview() {
        LeaveRequest leave = new LeaveRequest();
        leave.setId(60L);
        leave.setUser(employee);
        leave.setStatus(LeaveStatus.PENDING);
        leave.setTeamLeaderDecision(ApprovalDecision.PENDING);
        leave.setHrDecision(ApprovalDecision.PENDING);
        leave.setRequestDate(LocalDateTime.now());
        leave.setLeaveType(LeaveType.ANNUAL);
        leave.setStartDate(LocalDate.now().plusDays(12));
        leave.setEndDate(LocalDate.now().plusDays(13));
        leave.setNumberOfDays(2);
        leave.setReason("Old reason text");

        LocalDate newStart = LocalDate.now().plusDays(5);
        LocalDate newEnd = newStart.plusDays(2);
        CreateLeaveRequestDto update = dto(LeaveType.SICK, newStart, 3);

        when(leaveRequestRepository.findById(60L)).thenReturn(Optional.of(leave));
        when(workingDayService.isWorkingDay(newStart)).thenReturn(true);
        when(workingDayService.isWorkingDay(newEnd)).thenReturn(true);
        when(workingDayService.countWorkingDays(newStart, newEnd)).thenReturn(3);
        when(leaveRequestRepository.findByUserAndDateRangeAndStatusIn(eq(employee), eq(newStart), eq(newEnd), anyList()))
                .thenReturn(List.of(leave));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LeaveRequestResponseDto response = service.updateMyLeaveRequest(60L, employee.getKeycloakId(), update);

        assertThat(response.getId()).isEqualTo(60L);
        assertThat(response.getLeaveType()).isEqualTo(LeaveType.SICK);
        assertThat(response.getStartDate()).isEqualTo(newStart);
        assertThat(response.getEndDate()).isEqualTo(newEnd);
        assertThat(response.getNumberOfDays()).isEqualTo(3);
        assertThat(response.getReason()).isEqualTo("Family need");

        verify(leaveBalanceService).releaseReserved(leave);
        verify(leaveBalanceService).reserveForRequest(employee, LeaveType.SICK, newStart, 3);
        verify(leaveRequestRepository).save(argThat(saved ->
                saved.getId().equals(60L)
                        && saved.getLeaveType() == LeaveType.SICK
                        && saved.getStartDate().equals(newStart)
                        && saved.getEndDate().equals(newEnd)
                        && saved.getNumberOfDays() == 3
        ));
    }

    @Test
    void updateMyLeaveRequest_rejectsPendingHrLeave() {
        LeaveRequest leave = new LeaveRequest();
        leave.setId(61L);
        leave.setUser(employee);
        leave.setStatus(LeaveStatus.PENDING);
        leave.setTeamLeaderDecision(ApprovalDecision.APPROVED);
        leave.setHrDecision(ApprovalDecision.PENDING);
        leave.setRequestDate(LocalDateTime.now());

        when(leaveRequestRepository.findById(61L)).thenReturn(Optional.of(leave));

        assertThatThrownBy(() -> service.updateMyLeaveRequest(61L, employee.getKeycloakId(), dto(LeaveType.SICK, LocalDate.now().plusDays(2), 1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Only leave requests still pending Team Leader review can be edited by the employee.");

        verify(leaveBalanceService, never()).releaseReserved(any());
        verify(leaveRequestRepository, never()).save(any());
    }

    @Test
    void createLeaveRequest_rejectsWeekendStartDate() {
        LocalDate start = LocalDate.now().plusDays(10);

        when(workingDayService.isWorkingDay(start)).thenReturn(false);

        assertThatThrownBy(() -> service.createLeaveRequest("employee", dto(LeaveType.SICK, start, 1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Leave cannot start on a weekend or holiday.");

        verify(workingDayService, never()).countWorkingDays(any(), any());
        verifyNoInteractions(leaveBalanceService);
        verify(leaveRequestRepository, never()).save(any());
    }

    @Test
    void createLeaveRequest_rejectsHolidayEndDate() {
        LocalDate start = LocalDate.now().plusDays(10);
        LocalDate end = start.plusDays(2);

        when(workingDayService.isWorkingDay(start)).thenReturn(true);
        when(workingDayService.isWorkingDay(end)).thenReturn(false);

        assertThatThrownBy(() -> service.createLeaveRequest("employee", dto(LeaveType.ANNUAL, start, 3)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Leave cannot end on a weekend or holiday.");

        verify(workingDayService, never()).countWorkingDays(any(), any());
        verify(leaveBalanceService, never()).reserveForRequest(any(), any(), any(), anyInt());
        verify(leaveRequestRepository, never()).save(any());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private LeaveRequest pendingLeave(Long id, User user) {
        LeaveRequest leave = new LeaveRequest();
        leave.setId(id);
        leave.setUser(user);
        leave.setStatus(LeaveStatus.PENDING);
        leave.setTeamLeaderDecision(ApprovalDecision.PENDING);
        leave.setHrDecision(ApprovalDecision.PENDING);
        leave.setLeaveType(LeaveType.ANNUAL);
        leave.setStartDate(LocalDate.now().plusDays(10));
        leave.setEndDate(LocalDate.now().plusDays(12));
        leave.setNumberOfDays(3);
        leave.setReason("Family need");
        leave.setRequestDate(LocalDateTime.now());
        return leave;
    }

    private User teamLeader(String keycloakId) {
        User leader = new User(keycloakId, "leader");
        leader.setId(20L);
        leader.setRole(TypeRole.TEAM_LEADER);
        leader.setActive(true);
        leader.setPerson(new Person("Lead", "Tara", "tara.lead@example.com"));
        return leader;
    }

    private User hrManager(String keycloakId) {
        User hr = new User(keycloakId, "hr.manager");
        hr.setId(30L);
        hr.setRole(TypeRole.HR_MANAGER);
        hr.setActive(true);
        hr.setPerson(new Person("Manager", "Hana", "hana.manager@example.com"));
        return hr;
    }

    private Team team(Long id, User leader) {
        Team team = new Team();
        team.setId(id);
        team.setName("Engineering");
        team.setTeamLeader(leader);
        leader.setTeam(team);
        return team;
    }

    private CreateLeaveRequestDto dto(LeaveType type, LocalDate start, int days) {
        CreateLeaveRequestDto dto = new CreateLeaveRequestDto();
        dto.setLeaveType(type);
        dto.setStartDate(start);
        dto.setEndDate(start.plusDays(days - 1L));
        dto.setNumberOfDays(days);
        dto.setReason("Family need");
        return dto;
    }
}
