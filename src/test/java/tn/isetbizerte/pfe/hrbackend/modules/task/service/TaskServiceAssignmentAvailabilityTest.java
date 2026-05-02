package tn.isetbizerte.pfe.hrbackend.modules.task.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.isetbizerte.pfe.hrbackend.common.enums.AuthorizationType;
import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveStatus;
import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveType;
import tn.isetbizerte.pfe.hrbackend.common.enums.RequestStatus;
import tn.isetbizerte.pfe.hrbackend.common.enums.TaskAssignmentMode;
import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;
import tn.isetbizerte.pfe.hrbackend.common.exception.BadRequestException;
import tn.isetbizerte.pfe.hrbackend.infrastructure.email.HREmailService;
import tn.isetbizerte.pfe.hrbackend.infrastructure.kafka.producer.NotificationEventProducer;
import tn.isetbizerte.pfe.hrbackend.modules.calendar.service.WorkingDayService;
import tn.isetbizerte.pfe.hrbackend.modules.employee.entity.LeaveRequest;
import tn.isetbizerte.pfe.hrbackend.modules.employee.repository.LeaveRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.notification.service.NotificationService;
import tn.isetbizerte.pfe.hrbackend.modules.requests.entity.AuthorizationRequest;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.AuthorizationRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.task.dto.CreateTaskRequest;
import tn.isetbizerte.pfe.hrbackend.modules.task.entity.Project;
import tn.isetbizerte.pfe.hrbackend.modules.task.repository.ProjectRepository;
import tn.isetbizerte.pfe.hrbackend.modules.task.repository.TaskRepository;
import tn.isetbizerte.pfe.hrbackend.modules.team.entity.Team;
import tn.isetbizerte.pfe.hrbackend.modules.team.repository.TeamRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceAssignmentAvailabilityTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private UserRepository userRepository;
    @Mock private LeaveRequestRepository leaveRequestRepository;
    @Mock private AuthorizationRequestRepository authorizationRequestRepository;
    @Mock private WorkingDayService workingDayService;
    @Mock private NotificationEventProducer notificationEventProducer;
    @Mock private HREmailService hrEmailService;
    @Mock private NotificationService notificationService;

    private TaskService service;
    private Team team;
    private Project project;
    private User leader;
    private User assignee;

    @BeforeEach
    void setUp() {
        service = new TaskService(
                projectRepository,
                taskRepository,
                teamRepository,
                userRepository,
                leaveRequestRepository,
                authorizationRequestRepository,
                workingDayService,
                notificationEventProducer,
                hrEmailService,
                notificationService
        );

        leader = user(10L, "kc-leader", "leader", TypeRole.TEAM_LEADER);
        assignee = user(20L, "kc-employee", "employee", TypeRole.EMPLOYEE);

        team = new Team();
        team.setId(100L);
        team.setName("Engineering");
        team.setTeamLeader(leader);
        team.setMembers(List.of(assignee));
        leader.setTeam(team);
        assignee.setTeam(team);

        project = new Project();
        project.setId(500L);
        project.setName("Payroll Upgrade");
        project.setTeam(team);

        lenient().when(teamRepository.findByTeamLeaderKeycloakId("kc-leader")).thenReturn(Optional.of(team));
        lenient().when(projectRepository.findById(500L)).thenReturn(Optional.of(project));
        lenient().when(userRepository.findById(20L)).thenReturn(Optional.of(assignee));
        lenient().when(authorizationRequestRepository.findByUserAndAuthorizationTypeAndStatusAndAbsenceDateBetweenOrderByAbsenceDateAscFromTimeAsc(
                any(), any(), any(), any(), any()
        )).thenReturn(List.of());
        stubWorkingDays(Set.of());
    }

    @Test
    void previewTaskAssignment_blocksApprovedLeaveWhenNoWorkingDaysRemain() {
        LocalDate start = futureFridayTaskStart();
        LocalDate end = start.plusDays(6);
        when(leaveRequestRepository.findByUserIdAndDateRangeAndStatusIn(eq(20L), eq(start), eq(end), anyList()))
                .thenReturn(List.of(leave(LeaveStatus.APPROVED, start, end)));

        Map<String, Object> preview = service.previewTaskAssignment("kc-leader", 500L, task(start, end));

        assertThat(preview.get("canCreate")).isEqualTo(false);
        assertThat(preview.get("blockedCount")).isEqualTo(1);
        assertThat(preview.get("warnedCount")).isEqualTo(0);
        assertThat(reasons(preview, "blockedAssignees"))
                .containsExactly("Employee has no available working days during this task period.");
    }

    @Test
    void previewTaskAssignment_warnsApprovedLeaveWhenSomeWorkingDaysRemain() {
        LocalDate start = futureFridayTaskStart();
        LocalDate end = start.plusDays(6);
        LocalDate approvedLeaveStart = start.plusDays(2);
        LocalDate approvedLeaveEnd = start.plusDays(3);
        when(leaveRequestRepository.findByUserIdAndDateRangeAndStatusIn(eq(20L), eq(start), eq(end), anyList()))
                .thenReturn(List.of(leave(LeaveStatus.APPROVED, approvedLeaveStart, approvedLeaveEnd)));

        Map<String, Object> preview = service.previewTaskAssignment("kc-leader", 500L, task(start, end));

        assertThat(preview.get("canCreate")).isEqualTo(true);
        assertThat(preview.get("blockedCount")).isEqualTo(0);
        assertThat(preview.get("warnedCount")).isEqualTo(1);
        assertThat(reasons(preview, "warnedAssignees"))
                .containsExactly("Approved leave overlaps part of this task period. Employee still has 4 available working day(s).");
    }

    @Test
    void previewTaskAssignment_doesNotWarnWhenApprovedLeaveOnlyCoversWeekendDates() {
        LocalDate start = futureFridayTaskStart();
        LocalDate end = start.plusDays(6);
        when(leaveRequestRepository.findByUserIdAndDateRangeAndStatusIn(eq(20L), eq(start), eq(end), anyList()))
                .thenReturn(List.of(leave(LeaveStatus.APPROVED, start.plusDays(1), start.plusDays(2))));

        Map<String, Object> preview = service.previewTaskAssignment("kc-leader", 500L, task(start, end));

        assertThat(preview.get("canCreate")).isEqualTo(true);
        assertThat(preview.get("eligibleCount")).isEqualTo(1);
        assertThat(preview.get("warnedCount")).isEqualTo(0);
        assertThat(preview.get("blockedCount")).isEqualTo(0);
    }

    @Test
    void previewTaskAssignment_warnsPendingLeaveAndKeepsAssigneeCreatable() {
        LocalDate start = futureFridayTaskStart();
        LocalDate end = start.plusDays(6);
        LocalDate pendingLeaveStart = start.plusDays(3);
        LocalDate pendingLeaveEnd = start.plusDays(4);
        when(leaveRequestRepository.findByUserIdAndDateRangeAndStatusIn(eq(20L), eq(start), eq(end), anyList()))
                .thenReturn(List.of(leave(LeaveStatus.PENDING, pendingLeaveStart, pendingLeaveEnd)));

        Map<String, Object> preview = service.previewTaskAssignment("kc-leader", 500L, task(start, end));

        assertThat(preview.get("canCreate")).isEqualTo(true);
        assertThat(preview.get("warnedCount")).isEqualTo(1);
        assertThat(reasons(preview, "warnedAssignees"))
                .containsExactly("Pending leave overlaps " + pendingLeaveStart + " to " + pendingLeaveEnd);
    }

    @Test
    void previewTaskAssignment_warnsApprovedPartialAndPendingLeaveTogether() {
        LocalDate start = futureFridayTaskStart();
        LocalDate end = start.plusDays(6);
        LocalDate approvedLeave = start.plusDays(3);
        LocalDate pendingLeave = start.plusDays(5);
        when(leaveRequestRepository.findByUserIdAndDateRangeAndStatusIn(eq(20L), eq(start), eq(end), anyList()))
                .thenReturn(List.of(
                        leave(LeaveStatus.APPROVED, approvedLeave, approvedLeave),
                        leave(LeaveStatus.PENDING, pendingLeave, pendingLeave)
                ));

        Map<String, Object> preview = service.previewTaskAssignment("kc-leader", 500L, task(start, end));

        assertThat(preview.get("canCreate")).isEqualTo(true);
        assertThat(preview.get("warnedCount")).isEqualTo(1);
        assertThat(reasons(preview, "warnedAssignees"))
                .containsExactly(
                        "Approved leave overlaps part of this task period. Employee still has 4 available working day(s).",
                        "Pending leave overlaps " + pendingLeave + " to " + pendingLeave
                );
    }

    @Test
    void createTask_throwsExistingNoEligibleAssigneesMessageWhenAllSelectedAreUnavailable() {
        LocalDate start = futureFridayTaskStart();
        LocalDate end = start.plusDays(6);
        when(leaveRequestRepository.findByUserIdAndDateRangeAndStatusIn(eq(20L), eq(start), eq(end), anyList()))
                .thenReturn(List.of(leave(LeaveStatus.APPROVED, start, end)));

        assertThatThrownBy(() -> service.createTask("kc-leader", 500L, task(start, end)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("No eligible assignees available for this task. Resolve blocked assignment conflicts first.");

        verify(taskRepository, never()).save(any());
    }

    @Test
    void previewTaskAssignment_keepsWeekendStartValidationAsBlockedDateIssue() {
        LocalDate start = futureSaturday();
        LocalDate end = start.plusDays(5);

        Map<String, Object> preview = service.previewTaskAssignment("kc-leader", 500L, task(start, end));

        assertThat(preview.get("canCreate")).isEqualTo(false);
        assertThat(preview.get("blockedCount")).isEqualTo(1);
        assertThat(reasons(preview, "blockedAssignees")).containsExactly("Start date falls on a weekend.");
        verify(leaveRequestRepository, never()).findByUserIdAndDateRangeAndStatusIn(any(), any(), any(), anyList());
    }

    @Test
    void previewTaskAssignment_warnsApprovedShortAbsenceInsideTaskRangeAndKeepsAssigneeCreatable() {
        LocalDate start = futureFridayTaskStart();
        LocalDate end = start.plusDays(6);
        LocalDate absenceDate = start.plusDays(3);
        when(leaveRequestRepository.findByUserIdAndDateRangeAndStatusIn(eq(20L), eq(start), eq(end), anyList()))
                .thenReturn(List.of());
        when(authorizationRequestRepository.findByUserAndAuthorizationTypeAndStatusAndAbsenceDateBetweenOrderByAbsenceDateAscFromTimeAsc(
                eq(assignee),
                eq(AuthorizationType.TIME_PERMISSION),
                eq(RequestStatus.APPROVED),
                eq(start),
                eq(end)
        )).thenReturn(List.of(shortAbsence(absenceDate, LocalTime.of(10, 0), LocalTime.of(12, 0))));

        Map<String, Object> preview = service.previewTaskAssignment("kc-leader", 500L, task(start, end));

        assertThat(preview.get("canCreate")).isEqualTo(true);
        assertThat(preview.get("blockedCount")).isEqualTo(0);
        assertThat(preview.get("warnedCount")).isEqualTo(1);
        assertThat(reasons(preview, "warnedAssignees"))
                .containsExactly("Approved short absence on " + absenceDate + " from 10:00 to 12:00.");
    }

    @Test
    void previewTaskAssignment_doesNotWarnWhenApprovedShortAbsenceIsOutsideTaskRange() {
        LocalDate start = futureFridayTaskStart();
        LocalDate end = start.plusDays(6);
        when(leaveRequestRepository.findByUserIdAndDateRangeAndStatusIn(eq(20L), eq(start), eq(end), anyList()))
                .thenReturn(List.of());
        when(authorizationRequestRepository.findByUserAndAuthorizationTypeAndStatusAndAbsenceDateBetweenOrderByAbsenceDateAscFromTimeAsc(
                eq(assignee),
                eq(AuthorizationType.TIME_PERMISSION),
                eq(RequestStatus.APPROVED),
                eq(start),
                eq(end)
        )).thenReturn(List.of());

        Map<String, Object> preview = service.previewTaskAssignment("kc-leader", 500L, task(start, end));

        assertThat(preview.get("canCreate")).isEqualTo(true);
        assertThat(preview.get("eligibleCount")).isEqualTo(1);
        assertThat(preview.get("warnedCount")).isEqualTo(0);
        assertThat(preview.get("blockedCount")).isEqualTo(0);
    }

    @Test
    void previewTaskAssignment_combinesShortAbsenceWithApprovedPartialAndPendingLeaveWarnings() {
        LocalDate start = futureFridayTaskStart();
        LocalDate end = start.plusDays(6);
        LocalDate approvedLeave = start.plusDays(3);
        LocalDate shortAbsenceDate = start.plusDays(4);
        LocalDate pendingLeave = start.plusDays(5);
        when(leaveRequestRepository.findByUserIdAndDateRangeAndStatusIn(eq(20L), eq(start), eq(end), anyList()))
                .thenReturn(List.of(
                        leave(LeaveStatus.APPROVED, approvedLeave, approvedLeave),
                        leave(LeaveStatus.PENDING, pendingLeave, pendingLeave)
                ));
        when(authorizationRequestRepository.findByUserAndAuthorizationTypeAndStatusAndAbsenceDateBetweenOrderByAbsenceDateAscFromTimeAsc(
                eq(assignee),
                eq(AuthorizationType.TIME_PERMISSION),
                eq(RequestStatus.APPROVED),
                eq(start),
                eq(end)
        )).thenReturn(List.of(shortAbsence(shortAbsenceDate, LocalTime.of(9, 30), LocalTime.of(11, 15))));

        Map<String, Object> preview = service.previewTaskAssignment("kc-leader", 500L, task(start, end));

        assertThat(preview.get("canCreate")).isEqualTo(true);
        assertThat(preview.get("warnedCount")).isEqualTo(1);
        assertThat(reasons(preview, "warnedAssignees"))
                .containsExactly(
                        "Approved leave overlaps part of this task period. Employee still has 4 available working day(s).",
                        "Pending leave overlaps " + pendingLeave + " to " + pendingLeave,
                        "Approved short absence on " + shortAbsenceDate + " from 09:30 to 11:15."
                );
    }

    @Test
    void previewTaskAssignment_keepsFullApprovedLeaveBlockedEvenWhenShortAbsenceExists() {
        LocalDate start = futureFridayTaskStart();
        LocalDate end = start.plusDays(6);
        when(leaveRequestRepository.findByUserIdAndDateRangeAndStatusIn(eq(20L), eq(start), eq(end), anyList()))
                .thenReturn(List.of(leave(LeaveStatus.APPROVED, start, end)));

        Map<String, Object> preview = service.previewTaskAssignment("kc-leader", 500L, task(start, end));

        assertThat(preview.get("canCreate")).isEqualTo(false);
        assertThat(preview.get("blockedCount")).isEqualTo(1);
        assertThat(preview.get("warnedCount")).isEqualTo(0);
        assertThat(reasons(preview, "blockedAssignees"))
                .containsExactly("Employee has no available working days during this task period.");
        verify(authorizationRequestRepository, never())
                .findByUserAndAuthorizationTypeAndStatusAndAbsenceDateBetweenOrderByAbsenceDateAscFromTimeAsc(
                        any(), any(), any(), any(), any()
                );
    }

    @Test
    void createTask_createsTaskWhenOnlyShortAbsenceWarningExists() {
        LocalDate start = futureFridayTaskStart();
        LocalDate end = start.plusDays(6);
        LocalDate absenceDate = start.plusDays(3);
        when(leaveRequestRepository.findByUserIdAndDateRangeAndStatusIn(eq(20L), eq(start), eq(end), anyList()))
                .thenReturn(List.of());
        when(authorizationRequestRepository.findByUserAndAuthorizationTypeAndStatusAndAbsenceDateBetweenOrderByAbsenceDateAscFromTimeAsc(
                eq(assignee),
                eq(AuthorizationType.TIME_PERMISSION),
                eq(RequestStatus.APPROVED),
                eq(start),
                eq(end)
        )).thenReturn(List.of(shortAbsence(absenceDate, LocalTime.of(10, 0), LocalTime.of(12, 0))));

        Map<String, Object> result = service.createTask("kc-leader", 500L, task(start, end));

        assertThat(result.get("createdCount")).isEqualTo(1);
        assertThat(result.get("warnedCount")).isEqualTo(1);
        verify(taskRepository).save(any());
    }

    private void stubWorkingDays(Set<LocalDate> holidays) {
        lenient().when(workingDayService.isWorkingDay(any(LocalDate.class))).thenAnswer(invocation -> {
            LocalDate date = invocation.getArgument(0);
            return date != null
                    && date.getDayOfWeek() != DayOfWeek.SATURDAY
                    && date.getDayOfWeek() != DayOfWeek.SUNDAY
                    && !holidays.contains(date);
        });
        lenient().when(workingDayService.countWorkingDays(any(LocalDate.class), any(LocalDate.class))).thenAnswer(invocation -> {
            LocalDate start = invocation.getArgument(0);
            LocalDate end = invocation.getArgument(1);
            int count = 0;
            LocalDate current = start;
            while (!current.isAfter(end)) {
                if (current.getDayOfWeek() != DayOfWeek.SATURDAY
                        && current.getDayOfWeek() != DayOfWeek.SUNDAY
                        && !holidays.contains(current)) {
                    count++;
                }
                current = current.plusDays(1);
            }
            return count;
        });
        lenient().when(workingDayService.isPublicHoliday(any(LocalDate.class)))
                .thenAnswer(invocation -> holidays.contains(invocation.getArgument(0)));
    }

    private LocalDate futureWorkingDay(int offset) {
        LocalDate date = LocalDate.now().plusDays(1);
        int remaining = offset;
        while (true) {
            if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                if (remaining == 0) {
                    return date;
                }
                remaining--;
            }
            date = date.plusDays(1);
        }
    }

    private LocalDate futureFridayTaskStart() {
        LocalDate date = futureWorkingDay(0);
        while (date.getDayOfWeek() != DayOfWeek.FRIDAY) {
            date = date.plusDays(1);
        }
        return date;
    }

    private LocalDate futureSaturday() {
        LocalDate date = LocalDate.now().plusDays(1);
        while (date.getDayOfWeek() != DayOfWeek.SATURDAY) {
            date = date.plusDays(1);
        }
        return date;
    }

    private User user(Long id, String keycloakId, String username, TypeRole role) {
        User user = new User(keycloakId, username);
        user.setId(id);
        user.setRole(role);
        user.setActive(true);
        return user;
    }

    private CreateTaskRequest task(LocalDate start, LocalDate end) {
        CreateTaskRequest request = new CreateTaskRequest();
        request.setTitle("Prepare release");
        request.setStartDate(start);
        request.setDueDate(end);
        request.setAssignmentMode(TaskAssignmentMode.ONE);
        request.setAssigneeId(20L);
        return request;
    }

    private LeaveRequest leave(LeaveStatus status, LocalDate start, LocalDate end) {
        LeaveRequest leave = new LeaveRequest();
        leave.setUser(assignee);
        leave.setLeaveType(LeaveType.ANNUAL);
        leave.setStatus(status);
        leave.setStartDate(start);
        leave.setEndDate(end);
        leave.setNumberOfDays(1);
        return leave;
    }

    private AuthorizationRequest shortAbsence(LocalDate date, LocalTime from, LocalTime to) {
        AuthorizationRequest request = new AuthorizationRequest();
        request.setUser(assignee);
        request.setAuthorizationType(AuthorizationType.TIME_PERMISSION);
        request.setStatus(RequestStatus.APPROVED);
        request.setAbsenceDate(date);
        request.setStartDate(date);
        request.setEndDate(date);
        request.setFromTime(from);
        request.setToTime(to);
        return request;
    }

    @SuppressWarnings("unchecked")
    private List<String> reasons(Map<String, Object> preview, String bucket) {
        List<Map<String, Object>> assignees = (List<Map<String, Object>>) preview.get(bucket);
        assertThat(assignees).hasSize(1);
        return (List<String>) assignees.get(0).get("reasons");
    }
}
