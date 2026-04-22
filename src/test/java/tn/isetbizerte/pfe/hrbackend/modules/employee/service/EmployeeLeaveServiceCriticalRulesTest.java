package tn.isetbizerte.pfe.hrbackend.modules.employee.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.isetbizerte.pfe.hrbackend.common.enums.ApprovalDecision;
import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveStatus;
import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveType;
import tn.isetbizerte.pfe.hrbackend.common.exception.BadRequestException;
import tn.isetbizerte.pfe.hrbackend.infrastructure.email.HREmailService;
import tn.isetbizerte.pfe.hrbackend.infrastructure.kafka.producer.RequestEventProducer;
import tn.isetbizerte.pfe.hrbackend.modules.calendar.service.WorkingDayService;
import tn.isetbizerte.pfe.hrbackend.modules.employee.dto.CreateLeaveRequestDto;
import tn.isetbizerte.pfe.hrbackend.modules.employee.dto.LeaveRequestResponseDto;
import tn.isetbizerte.pfe.hrbackend.modules.employee.entity.LeaveRequest;
import tn.isetbizerte.pfe.hrbackend.modules.employee.repository.LeaveRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.history.service.RequestHistoryService;
import tn.isetbizerte.pfe.hrbackend.modules.team.repository.TeamRepository;
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
                workingDayService
        );
        employee = new User("kc-employee", "employee");
        employee.setId(10L);
        employee.setActive(true);
        lenient().when(userRepository.findByUsername("employee")).thenReturn(Optional.of(employee));
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
    void cancelMyLeaveRequest_allowsEmployeeCancellationWhilePendingHrReview() {
        LeaveRequest leave = new LeaveRequest();
        leave.setId(52L);
        leave.setUser(employee);
        leave.setStatus(LeaveStatus.PENDING);
        leave.setTeamLeaderDecision(ApprovalDecision.APPROVED);
        leave.setHrDecision(ApprovalDecision.PENDING);
        leave.setRequestDate(LocalDateTime.now());

        when(leaveRequestRepository.findById(52L)).thenReturn(Optional.of(leave));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.cancelMyLeaveRequest(52L, employee.getKeycloakId());

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
