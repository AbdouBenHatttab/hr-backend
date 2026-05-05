package tn.isetbizerte.pfe.hrbackend.modules.employee.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveStatus;
import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveType;
import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;
import tn.isetbizerte.pfe.hrbackend.modules.calendar.dto.WorkingDaysEstimateDto;
import tn.isetbizerte.pfe.hrbackend.modules.calendar.service.WorkingDayService;
import tn.isetbizerte.pfe.hrbackend.modules.employee.dto.LeaveDraftValidationRequestDto;
import tn.isetbizerte.pfe.hrbackend.modules.employee.dto.LeaveDraftValidationResponseDto;
import tn.isetbizerte.pfe.hrbackend.modules.employee.entity.LeaveRequest;
import tn.isetbizerte.pfe.hrbackend.modules.employee.repository.LeaveRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.team.entity.Team;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LeaveValidationService.validateDraft().
 *
 * Covers every requirement from Phase 3.2:
 * - Valid annual leave returns valid=true with working-day info and balance preview.
 * - Annual leave starting today → valid=false.
 * - Annual leave > 6 months ahead → valid=false.
 * - Annual leave with insufficient balance → valid=false.
 * - Sick leave > 7 days ahead → valid=false.
 * - Unpaid leave skips balance check (balanceManaged=false).
 * - Past start date → valid=false (structured error, no exception).
 * - Start after end → valid=false (structured error).
 * - Weekend / holiday start or end → valid=false.
 * - Overlap with PENDING/APPROVED leave → valid=false.
 * - TEAM_LEADER draft predicts initialStage=PENDING_HR.
 * - EMPLOYEE draft predicts initialStage=PENDING_TL.
 * - validateDraft never calls leaveRequestRepository.save() (no DB write).
 * - validateDraft never calls leaveBalanceService.reserveForRequest() (no balance reservation).
 * - Missing required fields return missingFields errors without crashing.
 */
@ExtendWith(MockitoExtension.class)
class LeaveValidationServiceTest {

    @Mock private WorkingDayService workingDayService;
    @Mock private LeaveBalanceService leaveBalanceService;
    @Mock private LeaveRequestRepository leaveRequestRepository;

    private LeaveValidationService service;

    // A standard EMPLOYEE user with a properly configured team.
    private User employee;
    // A TEAM_LEADER user.
    private User teamLeader;

    @BeforeEach
    void setUp() {
        service = new LeaveValidationService(workingDayService, leaveBalanceService, leaveRequestRepository);

        User leader = new User("kc-leader", "leader");
        leader.setId(20L);
        leader.setRole(TypeRole.TEAM_LEADER);
        leader.setActive(true);

        Team team = new Team();
        team.setId(1L);
        team.setName("Engineering");
        team.setTeamLeader(leader);
        leader.setTeam(team);

        employee = new User("kc-employee", "employee");
        employee.setId(10L);
        employee.setRole(TypeRole.EMPLOYEE);
        employee.setActive(true);
        employee.setTeam(team);

        teamLeader = leader;
    }

    // -----------------------------------------------------------------------
    // Missing-fields tests
    // -----------------------------------------------------------------------

    @Test
    void validateDraft_missingLeaveType_returnsValidFalseWithError() {
        LeaveDraftValidationRequestDto dto = request(null, LocalDate.now().plusDays(5), LocalDate.now().plusDays(6));

        LeaveDraftValidationResponseDto result = service.validateDraft(employee, dto);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("Leave type is required"));
        verifyNoInteractions(workingDayService, leaveRequestRepository);
        verify(leaveBalanceService, never()).reserveForRequest(any(), any(), any(), anyInt());
    }

    @Test
    void validateDraft_missingStartDate_returnsValidFalseWithError() {
        LeaveDraftValidationRequestDto dto = request(LeaveType.SICK, null, LocalDate.now().plusDays(3));

        LeaveDraftValidationResponseDto result = service.validateDraft(employee, dto);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("Start date is required"));
        verifyNoInteractions(workingDayService, leaveRequestRepository);
        verify(leaveBalanceService, never()).reserveForRequest(any(), any(), any(), anyInt());
    }

    @Test
    void validateDraft_missingEndDate_returnsValidFalseWithError() {
        LeaveDraftValidationRequestDto dto = request(LeaveType.SICK, LocalDate.now().plusDays(2), null);

        LeaveDraftValidationResponseDto result = service.validateDraft(employee, dto);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("End date is required"));
        verifyNoInteractions(workingDayService, leaveRequestRepository);
        verify(leaveBalanceService, never()).reserveForRequest(any(), any(), any(), anyInt());
    }

    // -----------------------------------------------------------------------
    // Date order and past-date tests
    // -----------------------------------------------------------------------

    @Test
    void validateDraft_pastStartDate_returnsValidFalseWithStructuredError_notException() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LeaveDraftValidationRequestDto dto = request(LeaveType.SICK, yesterday, yesterday);

        // Must NOT throw; must return structured result
        LeaveDraftValidationResponseDto result = service.validateDraft(employee, dto);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("Start date cannot be in the past"));
        verify(leaveBalanceService, never()).reserveForRequest(any(), any(), any(), anyInt());
    }

    @Test
    void validateDraft_startAfterEnd_returnsValidFalseWithStructuredError() {
        LocalDate start = LocalDate.now().plusDays(5);
        LocalDate end   = LocalDate.now().plusDays(3);
        LeaveDraftValidationRequestDto dto = request(LeaveType.SICK, start, end);

        LeaveDraftValidationResponseDto result = service.validateDraft(employee, dto);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("Start date must be before or equal to end date"));
        verify(leaveBalanceService, never()).reserveForRequest(any(), any(), any(), anyInt());
    }

    // -----------------------------------------------------------------------
    // Working-day boundary tests
    // -----------------------------------------------------------------------

    @Test
    void validateDraft_weekendStartDate_returnsValidFalse() {
        LocalDate start = LocalDate.now().plusDays(5);
        LocalDate end   = start;
        when(workingDayService.isWorkingDay(start)).thenReturn(false);

        LeaveDraftValidationResponseDto result = service.validateDraft(employee, request(LeaveType.SICK, start, end));

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("Leave cannot start on a weekend or public holiday"));
        verify(leaveBalanceService, never()).reserveForRequest(any(), any(), any(), anyInt());
    }

    @Test
    void validateDraft_holidayEndDate_returnsValidFalse() {
        LocalDate start = LocalDate.now().plusDays(5);
        LocalDate end   = start.plusDays(2);
        when(workingDayService.isWorkingDay(start)).thenReturn(true);
        when(workingDayService.isWorkingDay(end)).thenReturn(false);

        LeaveDraftValidationResponseDto result = service.validateDraft(employee, request(LeaveType.ANNUAL, start, end));

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("Leave cannot end on a weekend or public holiday"));
        verify(leaveBalanceService, never()).reserveForRequest(any(), any(), any(), anyInt());
    }

    // -----------------------------------------------------------------------
    // Annual leave specific rules
    // -----------------------------------------------------------------------

    @Test
    void validateDraft_annualLeaveStartingToday_returnsValidFalse() {
        LocalDate today = LocalDate.now();
        when(workingDayService.isWorkingDay(today)).thenReturn(true);
        when(workingDayService.countWorkingDays(today, today)).thenReturn(1);
        when(workingDayService.estimate(today, today)).thenReturn(emptyEstimate(today, today, 1));
        when(leaveRequestRepository.findByUserAndDateRangeAndStatusIn(eq(employee), eq(today), eq(today), anyList()))
                .thenReturn(List.of());
        when(leaveBalanceService.isBalanceManaged(LeaveType.ANNUAL)).thenReturn(true);

        LeaveDraftValidationResponseDto result = service.validateDraft(employee, request(LeaveType.ANNUAL, today, today));

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("Annual leave must be requested at least 1 day in advance"));
        verify(leaveBalanceService, never()).reserveForRequest(any(), any(), any(), anyInt());
    }

    @Test
    void validateDraft_annualLeaveMoreThanSixMonthsAhead_returnsValidFalse() {
        LocalDate start = LocalDate.now().plusMonths(6).plusDays(1);
        when(workingDayService.isWorkingDay(start)).thenReturn(true);
        when(workingDayService.countWorkingDays(start, start)).thenReturn(1);
        when(workingDayService.estimate(start, start)).thenReturn(emptyEstimate(start, start, 1));
        when(leaveRequestRepository.findByUserAndDateRangeAndStatusIn(eq(employee), eq(start), eq(start), anyList()))
                .thenReturn(List.of());
        when(leaveBalanceService.isBalanceManaged(LeaveType.ANNUAL)).thenReturn(true);

        LeaveDraftValidationResponseDto result = service.validateDraft(employee, request(LeaveType.ANNUAL, start, start));

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("Annual leave cannot be requested more than 6 months in advance"));
        verify(leaveBalanceService, never()).reserveForRequest(any(), any(), any(), anyInt());
    }

    @Test
    void validateDraft_annualLeaveInsufficientBalance_returnsValidFalse() {
        LocalDate start = LocalDate.now().plusDays(10);
        LocalDate end   = start.plusDays(4);
        when(workingDayService.isWorkingDay(start)).thenReturn(true);
        when(workingDayService.isWorkingDay(end)).thenReturn(true);
        when(workingDayService.countWorkingDays(start, end)).thenReturn(5);
        when(workingDayService.estimate(start, end)).thenReturn(emptyEstimate(start, end, 5));
        when(leaveRequestRepository.findByUserAndDateRangeAndStatusIn(eq(employee), eq(start), eq(end), anyList()))
                .thenReturn(List.of());
        when(leaveBalanceService.isBalanceManaged(LeaveType.ANNUAL)).thenReturn(true);
        when(leaveBalanceService.getAvailableDays(employee, LeaveType.ANNUAL, start)).thenReturn(BigDecimal.valueOf(3));

        LeaveDraftValidationResponseDto result = service.validateDraft(employee, request(LeaveType.ANNUAL, start, end));

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("Insufficient annual leave balance"));
        verify(leaveBalanceService, never()).reserveForRequest(any(), any(), any(), anyInt());
    }

    @Test
    void validateDraft_validAnnualLeave_returnsValidTrueWithWorkingDaysAndBalance() {
        LocalDate start = LocalDate.now().plusDays(7);
        LocalDate end   = start.plusDays(4);
        when(workingDayService.isWorkingDay(start)).thenReturn(true);
        when(workingDayService.isWorkingDay(end)).thenReturn(true);
        when(workingDayService.countWorkingDays(start, end)).thenReturn(5);
        when(workingDayService.estimate(start, end)).thenReturn(emptyEstimate(start, end, 5));
        when(leaveRequestRepository.findByUserAndDateRangeAndStatusIn(eq(employee), eq(start), eq(end), anyList()))
                .thenReturn(List.of());
        when(leaveBalanceService.isBalanceManaged(LeaveType.ANNUAL)).thenReturn(true);
        when(leaveBalanceService.getAvailableDays(employee, LeaveType.ANNUAL, start)).thenReturn(BigDecimal.TEN);

        LeaveDraftValidationResponseDto result = service.validateDraft(employee, request(LeaveType.ANNUAL, start, end));

        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getWorkingDays()).isEqualTo(5);
        assertThat(result.getBalanceBefore()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(result.getBalanceAfterIfApproved()).isEqualByComparingTo(BigDecimal.valueOf(5));
        assertThat(result.getBalanceManaged()).isTrue();
        assertThat(result.getInitialStage()).isEqualTo("PENDING_TL");
        assertThat(result.getLeaveType()).isEqualTo(LeaveType.ANNUAL);
        // No DB write, no balance reservation
        verify(leaveRequestRepository, never()).save(any());
        verify(leaveBalanceService, never()).reserveForRequest(any(), any(), any(), anyInt());
    }

    // -----------------------------------------------------------------------
    // Sick leave rules
    // -----------------------------------------------------------------------

    @Test
    void validateDraft_sickLeaveMoreThanSevenDaysAhead_returnsValidFalse() {
        LocalDate start = LocalDate.now().plusDays(8);
        when(workingDayService.isWorkingDay(start)).thenReturn(true);
        when(workingDayService.countWorkingDays(start, start)).thenReturn(1);
        when(workingDayService.estimate(start, start)).thenReturn(emptyEstimate(start, start, 1));
        when(leaveRequestRepository.findByUserAndDateRangeAndStatusIn(eq(employee), eq(start), eq(start), anyList()))
                .thenReturn(List.of());
        when(leaveBalanceService.isBalanceManaged(LeaveType.SICK)).thenReturn(true);

        LeaveDraftValidationResponseDto result = service.validateDraft(employee, request(LeaveType.SICK, start, start));

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("Sick leave cannot be requested more than 7 days in advance"));
        verify(leaveBalanceService, never()).reserveForRequest(any(), any(), any(), anyInt());
    }

    // -----------------------------------------------------------------------
    // Unpaid leave — no balance check
    // -----------------------------------------------------------------------

    @Test
    void validateDraft_unpaidLeaveSkipsBalanceCheckAndIsValid() {
        LocalDate start = LocalDate.now().plusDays(3);
        LocalDate end   = start.plusDays(1);
        when(workingDayService.isWorkingDay(start)).thenReturn(true);
        when(workingDayService.isWorkingDay(end)).thenReturn(true);
        when(workingDayService.countWorkingDays(start, end)).thenReturn(2);
        when(workingDayService.estimate(start, end)).thenReturn(emptyEstimate(start, end, 2));
        when(leaveRequestRepository.findByUserAndDateRangeAndStatusIn(eq(employee), eq(start), eq(end), anyList()))
                .thenReturn(List.of());
        when(leaveBalanceService.isBalanceManaged(LeaveType.UNPAID)).thenReturn(false);

        LeaveDraftValidationResponseDto result = service.validateDraft(employee, request(LeaveType.UNPAID, start, end));

        assertThat(result.isValid()).isTrue();
        assertThat(result.getBalanceBefore()).isNull();
        assertThat(result.getBalanceAfterIfApproved()).isNull();
        assertThat(result.getBalanceManaged()).isFalse();
        // getAvailableDays must NOT be called for unmanaged leave
        verify(leaveBalanceService, never()).getAvailableDays(any(), any(), any());
        verify(leaveBalanceService, never()).reserveForRequest(any(), any(), any(), anyInt());
        verify(leaveRequestRepository, never()).save(any());
    }

    // -----------------------------------------------------------------------
    // Overlap check
    // -----------------------------------------------------------------------

    @Test
    void validateDraft_overlappingPendingLeave_returnsValidFalse() {
        LocalDate start = LocalDate.now().plusDays(5);
        LocalDate end   = start;
        when(workingDayService.isWorkingDay(start)).thenReturn(true);
        when(workingDayService.countWorkingDays(start, end)).thenReturn(1);
        when(workingDayService.estimate(start, end)).thenReturn(emptyEstimate(start, end, 1));

        LeaveRequest conflicting = new LeaveRequest();
        conflicting.setId(99L);
        conflicting.setStatus(LeaveStatus.PENDING);
        conflicting.setStartDate(start.minusDays(1));
        conflicting.setEndDate(start.plusDays(1));
        conflicting.setLeaveType(LeaveType.SICK);
        conflicting.setUser(employee);
        conflicting.setRequestDate(LocalDateTime.now());

        when(leaveRequestRepository.findByUserAndDateRangeAndStatusIn(
                eq(employee), eq(start), eq(end), anyList())).thenReturn(List.of(conflicting));
        when(leaveBalanceService.isBalanceManaged(LeaveType.SICK)).thenReturn(true);

        LeaveDraftValidationResponseDto result = service.validateDraft(employee, request(LeaveType.SICK, start, end));

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("overlaps with an existing pending leave request"));
        verify(leaveBalanceService, never()).reserveForRequest(any(), any(), any(), anyInt());
        verify(leaveRequestRepository, never()).save(any());
    }

    // -----------------------------------------------------------------------
    // initialStage prediction
    // -----------------------------------------------------------------------

    @Test
    void validateDraft_employeeDraftPredicts_PENDING_TL() {
        LocalDate start = LocalDate.now().plusDays(5);
        stubValidSickLeave(start);

        LeaveDraftValidationResponseDto result = service.validateDraft(employee, request(LeaveType.SICK, start, start));

        assertThat(result.isValid()).isTrue();
        assertThat(result.getInitialStage()).isEqualTo("PENDING_TL");
    }

    @Test
    void validateDraft_teamLeaderDraftPredicts_PENDING_HR() {
        LocalDate start = LocalDate.now().plusDays(5);
        stubValidSickLeave(start);

        LeaveDraftValidationResponseDto result = service.validateDraft(teamLeader, request(LeaveType.SICK, start, start));

        assertThat(result.isValid()).isTrue();
        assertThat(result.getInitialStage()).isEqualTo("PENDING_HR");
    }

    // -----------------------------------------------------------------------
    // No-side-effect guarantees
    // -----------------------------------------------------------------------

    @Test
    void validateDraft_neverSavesLeaveRequest() {
        LocalDate start = LocalDate.now().plusDays(5);
        stubValidSickLeave(start);

        service.validateDraft(employee, request(LeaveType.SICK, start, start));

        verify(leaveRequestRepository, never()).save(any());
    }

    @Test
    void validateDraft_neverReservesBalance() {
        LocalDate start = LocalDate.now().plusDays(5);
        stubValidSickLeave(start);

        service.validateDraft(employee, request(LeaveType.SICK, start, start));

        verify(leaveBalanceService, never()).reserveForRequest(any(), any(), any(), anyInt());
    }

    // -----------------------------------------------------------------------
    // Team setup for EMPLOYEE
    // -----------------------------------------------------------------------

    @Test
    void validateDraft_employeeWithoutTeam_returnsValidFalseWithTeamError() {
        employee.setTeam(null);
        LocalDate start = LocalDate.now().plusDays(5);
        when(workingDayService.isWorkingDay(start)).thenReturn(true);
        when(workingDayService.countWorkingDays(start, start)).thenReturn(1);
        when(workingDayService.estimate(start, start)).thenReturn(emptyEstimate(start, start, 1));

        LeaveDraftValidationResponseDto result = service.validateDraft(employee, request(LeaveType.SICK, start, start));

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("assigned to a team"));
        verify(leaveBalanceService, never()).reserveForRequest(any(), any(), any(), anyInt());
        verify(leaveRequestRepository, never()).save(any());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void stubValidSickLeave(LocalDate start) {
        when(workingDayService.isWorkingDay(start)).thenReturn(true);
        when(workingDayService.countWorkingDays(start, start)).thenReturn(1);
        when(workingDayService.estimate(start, start)).thenReturn(emptyEstimate(start, start, 1));
        when(leaveRequestRepository.findByUserAndDateRangeAndStatusIn(any(), any(), any(), anyList()))
                .thenReturn(List.of());
        when(leaveBalanceService.isBalanceManaged(LeaveType.SICK)).thenReturn(true);
        when(leaveBalanceService.getAvailableDays(any(), eq(LeaveType.SICK), any()))
                .thenReturn(BigDecimal.TEN);
    }

    private LeaveDraftValidationRequestDto request(LeaveType type, LocalDate start, LocalDate end) {
        LeaveDraftValidationRequestDto dto = new LeaveDraftValidationRequestDto();
        dto.setLeaveType(type);
        dto.setStartDate(start);
        dto.setEndDate(end);
        dto.setReason("Feeling unwell");
        return dto;
    }

    private WorkingDaysEstimateDto emptyEstimate(LocalDate start, LocalDate end, int days) {
        return new WorkingDaysEstimateDto(start, end, days, List.of(), List.of());
    }
}
