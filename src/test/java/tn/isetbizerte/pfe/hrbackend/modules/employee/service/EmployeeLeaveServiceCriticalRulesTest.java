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
    void createLeaveRequest_rejectsAnnualLeaveWhenRequestedDaysExceedAvailableBalance() {
        LocalDate start = LocalDate.now().plusDays(20);
        when(leaveRequestRepository.findByUserAndDateRangeAndStatusIn(
                eq(employee), eq(start), eq(start.plusDays(4)), anyList()
        )).thenReturn(List.of());
        when(workingDayService.countWorkingDays(start, start.plusDays(4))).thenReturn(5);
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
    void cancelMyLeaveRequest_rejectsEmployeeCancellationAfterTeamLeaderApproval() {
        LeaveRequest leave = new LeaveRequest();
        leave.setId(51L);
        leave.setUser(employee);
        leave.setStatus(LeaveStatus.PENDING);
        leave.setTeamLeaderDecision(ApprovalDecision.APPROVED);
        leave.setHrDecision(ApprovalDecision.PENDING);
        leave.setRequestDate(LocalDateTime.now());

        when(leaveRequestRepository.findById(51L)).thenReturn(Optional.of(leave));

        assertThatThrownBy(() -> service.cancelMyLeaveRequest(51L, employee.getKeycloakId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Only leave requests still pending Team Leader review can be canceled by the employee.");

        verify(leaveBalanceService, never()).releaseReserved(any());
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
