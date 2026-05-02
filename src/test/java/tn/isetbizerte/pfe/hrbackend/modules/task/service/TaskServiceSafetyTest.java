package tn.isetbizerte.pfe.hrbackend.modules.task.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.isetbizerte.pfe.hrbackend.common.enums.TaskAssignmentMode;
import tn.isetbizerte.pfe.hrbackend.common.enums.TaskPriority;
import tn.isetbizerte.pfe.hrbackend.common.enums.TaskStatus;
import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;
import tn.isetbizerte.pfe.hrbackend.common.event.NotificationEvent;
import tn.isetbizerte.pfe.hrbackend.common.exception.BadRequestException;
import tn.isetbizerte.pfe.hrbackend.common.exception.UnauthorizedException;
import tn.isetbizerte.pfe.hrbackend.infrastructure.email.HREmailService;
import tn.isetbizerte.pfe.hrbackend.infrastructure.kafka.producer.NotificationEventProducer;
import tn.isetbizerte.pfe.hrbackend.modules.calendar.service.WorkingDayService;
import tn.isetbizerte.pfe.hrbackend.modules.employee.repository.LeaveRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.notification.service.NotificationService;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.AuthorizationRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.task.dto.CreateProjectRequest;
import tn.isetbizerte.pfe.hrbackend.modules.task.dto.CreateTaskRequest;
import tn.isetbizerte.pfe.hrbackend.modules.task.dto.UpdateProjectRequest;
import tn.isetbizerte.pfe.hrbackend.modules.task.dto.UpdateTaskRequest;
import tn.isetbizerte.pfe.hrbackend.modules.task.entity.Project;
import tn.isetbizerte.pfe.hrbackend.modules.task.entity.Task;
import tn.isetbizerte.pfe.hrbackend.modules.task.repository.ProjectRepository;
import tn.isetbizerte.pfe.hrbackend.modules.task.repository.TaskRepository;
import tn.isetbizerte.pfe.hrbackend.modules.team.entity.Team;
import tn.isetbizerte.pfe.hrbackend.modules.team.repository.TeamRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.Person;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceSafetyTest {

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
    private User otherEmployee;

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
        otherEmployee = user(30L, "kc-other", "other", TypeRole.EMPLOYEE);

        leader.setPerson(person("Lead", "Owner", "leader@example.com"));
        assignee.setPerson(person("Ada", "Worker", "ada@example.com"));
        otherEmployee.setPerson(person("Other", "Worker", "other@example.com"));

        team = new Team();
        team.setId(100L);
        team.setName("Engineering");
        team.setTeamLeader(leader);
        team.setMembers(List.of(assignee, otherEmployee));
        leader.setTeam(team);
        assignee.setTeam(team);
        otherEmployee.setTeam(team);

        project = new Project();
        project.setId(500L);
        project.setName("Payroll Upgrade");
        project.setTeam(team);

        lenient().when(teamRepository.findByTeamLeaderKeycloakId("kc-leader")).thenReturn(Optional.of(team));
        lenient().when(projectRepository.findById(500L)).thenReturn(Optional.of(project));
        lenient().when(userRepository.findById(20L)).thenReturn(Optional.of(assignee));
        lenient().when(userRepository.findByKeycloakId("kc-leader")).thenReturn(Optional.of(leader));
        lenient().when(userRepository.findByKeycloakId("kc-employee")).thenReturn(Optional.of(assignee));
        lenient().when(userRepository.findByKeycloakId("kc-other")).thenReturn(Optional.of(otherEmployee));
        lenient().when(leaveRequestRepository.findByUserIdAndDateRangeAndStatusIn(any(), any(), any(), anyList()))
                .thenReturn(List.of());
        lenient().when(authorizationRequestRepository.findByUserAndAuthorizationTypeAndStatusAndAbsenceDateBetweenOrderByAbsenceDateAscFromTimeAsc(
                any(), any(), any(), any(), any()
        )).thenReturn(List.of());
        stubWorkingDays();
    }

    @Test
    void createProject_rejectsDuplicateNameInSameTeam() {
        when(projectRepository.existsByTeamIdAndNameIgnoreCase(100L, "Payroll Upgrade")).thenReturn(true);

        assertThatThrownBy(() -> service.createProject("kc-leader", projectCreate(" Payroll Upgrade ", "Duplicate")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("A project with this name already exists in your team.");

        verify(projectRepository, never()).save(any(Project.class));
    }

    @Test
    void createProject_rejectsDuplicateNameWithDifferentCasingInSameTeam() {
        when(projectRepository.existsByTeamIdAndNameIgnoreCase(100L, "payroll upgrade")).thenReturn(true);

        assertThatThrownBy(() -> service.createProject("kc-leader", projectCreate(" payroll upgrade ", "Duplicate")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("A project with this name already exists in your team.");

        verify(projectRepository, never()).save(any(Project.class));
    }

    @Test
    void createProject_allowsSameNameInDifferentTeam() {
        CreateProjectRequest request = projectCreate(" Payroll Upgrade ", "Allowed in another team");

        Map<String, Object> created = service.createProject("kc-leader", request);

        assertThat(created.get("name")).isEqualTo("Payroll Upgrade");
        verify(projectRepository).existsByTeamIdAndNameIgnoreCase(100L, "Payroll Upgrade");
        verify(projectRepository).save(any(Project.class));
    }

    @Test
    void createTask_sendsAssignmentEmailWithDescriptionBeforeProjectName() {
        LocalDate start = futureWorkingDay(0);
        LocalDate due = futureWorkingDay(3);
        CreateTaskRequest request = taskRequest(start, due);

        service.createTask("kc-leader", 500L, request);

        verify(hrEmailService).sendTaskAssigned(
                eq("ada@example.com"),
                eq("Ada"),
                eq("Worker"),
                eq("Prepare release"),
                eq("Prepare payroll release notes"),
                eq("Payroll Upgrade"),
                eq("HIGH"),
                eq(start),
                eq(due),
                eq("Lead Owner")
        );
    }

    @Test
    void updateProject_allowsKeepingOwnSameName() {
        UpdateProjectRequest request = projectUpdate(" Payroll Upgrade ", "Same name, new description");

        Map<String, Object> updated = service.updateProject("kc-leader", 500L, request);

        assertThat(updated.get("name")).isEqualTo("Payroll Upgrade");
        assertThat(updated.get("description")).isEqualTo("Same name, new description");
        verify(projectRepository).existsByTeamIdAndNameIgnoreCaseAndIdNot(100L, "Payroll Upgrade", 500L);
        verify(projectRepository).save(project);
    }

    @Test
    void updateProject_updatesOwnTeamProjectNameAndDescription() {
        UpdateProjectRequest request = projectUpdate("Payroll Stabilization", "Clean up payroll edge cases");

        Map<String, Object> updated = service.updateProject("kc-leader", 500L, request);

        assertThat(updated.get("name")).isEqualTo("Payroll Stabilization");
        assertThat(updated.get("description")).isEqualTo("Clean up payroll edge cases");
        verify(projectRepository).save(project);
    }

    @Test
    void updateProject_rejectsRenamingToAnotherSameTeamProjectName() {
        when(projectRepository.existsByTeamIdAndNameIgnoreCaseAndIdNot(100L, "Benefits Portal", 500L)).thenReturn(true);

        assertThatThrownBy(() -> service.updateProject("kc-leader", 500L, projectUpdate(" Benefits Portal ", "Duplicate")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("A project with this name already exists in your team.");

        verify(projectRepository, never()).save(project);
    }

    @Test
    void updateProject_rejectsProjectOutsideLeaderTeam() {
        Project otherProject = new Project();
        otherProject.setId(501L);
        otherProject.setName("Other Project");
        otherProject.setTeam(otherTeam());
        when(projectRepository.findById(501L)).thenReturn(Optional.of(otherProject));

        assertThatThrownBy(() -> service.updateProject("kc-leader", 501L, projectUpdate("Updated", "")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("This project does not belong to your team.");
    }

    @Test
    void createTask_rejectsPastStartDate() {
        LocalDate past = LocalDate.now().minusDays(1);
        LocalDate due = nextWorkingDay();

        assertThatThrownBy(() -> service.createTask("kc-leader", 500L, taskRequest(past, due)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Start date cannot be in the past.");

        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    void createTask_rejectsPastDueDate() {
        LocalDate past = LocalDate.now().minusDays(1);

        assertThatThrownBy(() -> service.createTask("kc-leader", 500L, taskRequest(null, past)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Due date cannot be in the past.");

        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    void createTask_allowsTodayWhenItIsAWorkingDay() {
        LocalDate today = LocalDate.now();
        when(workingDayService.isWorkingDay(today)).thenReturn(true);

        service.createTask("kc-leader", 500L, taskRequest(today, today));

        verify(taskRepository).save(any(Task.class));
    }

    @Test
    void updateTask_updatesOwnTeamTaskBasicFieldsAndPreservesStatusAssigneeProject() {
        Task task = task(TaskStatus.IN_PROGRESS);
        when(taskRepository.findById(900L)).thenReturn(Optional.of(task));

        Map<String, Object> updated = service.updateTask("kc-leader", 900L, taskUpdate(
                "Updated task",
                "Updated description",
                TaskPriority.LOW,
                futureWorkingDay(1),
                futureWorkingDay(4)
        ));

        assertThat(updated.get("title")).isEqualTo("Updated task");
        assertThat(updated.get("description")).isEqualTo("Updated description");
        assertThat(updated.get("priority")).isEqualTo("LOW");
        assertThat(updated.get("status")).isEqualTo("IN_PROGRESS");
        assertThat(updated.get("projectId")).isEqualTo(500L);
        assertThat(updated.get("assigneeId")).isEqualTo(20L);
        assertThat(updated.keySet()).containsExactlyInAnyOrder(
                "id",
                "title",
                "description",
                "status",
                "priority",
                "startDate",
                "dueDate",
                "projectId",
                "projectName",
                "projectDescription",
                "assigneeId",
                "assigneeName",
                "createdAt",
                "updatedAt"
        );
        verify(taskRepository).save(task);
    }

    @Test
    void updateTask_publishesTaskUpdatedNotificationForAssignedTask() {
        Task task = task(TaskStatus.IN_PROGRESS);
        when(taskRepository.findById(900L)).thenReturn(Optional.of(task));

        service.updateTask("kc-leader", 900L, taskUpdate(
                "Updated task",
                "Updated description",
                TaskPriority.LOW,
                futureWorkingDay(1),
                futureWorkingDay(4)
        ));

        ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationEventProducer).publish(eventCaptor.capture());
        NotificationEvent event = eventCaptor.getValue();
        assertThat(event.getUserId()).isEqualTo("kc-employee");
        assertThat(event.getMessage()).isEqualTo("Task updated: Updated task");
        assertThat(event.getType()).isEqualTo("TASK_UPDATED");
        assertThat(event.getReferenceType()).isEqualTo("TASK");
        assertThat(event.getReferenceId()).isEqualTo(900L);
        assertThat(event.getActionUrl()).isEqualTo("/employee/tasks?taskId=900");
        verifyNoInteractions(notificationService);
    }

    @Test
    void updateTask_sendsTaskUpdatedEmailForAssignedTaskWithEmail() {
        Task task = task(TaskStatus.IN_PROGRESS);
        LocalDate start = futureWorkingDay(1);
        LocalDate due = futureWorkingDay(4);
        when(taskRepository.findById(900L)).thenReturn(Optional.of(task));

        service.updateTask("kc-leader", 900L, taskUpdate(
                "Updated task",
                "Updated description",
                TaskPriority.LOW,
                start,
                due
        ));

        verify(hrEmailService).sendTaskUpdated(
                eq("ada@example.com"),
                eq("Ada"),
                eq("Worker"),
                eq("Updated task"),
                eq("Updated description"),
                eq("Payroll Upgrade"),
                eq("LOW"),
                eq(start),
                eq(due),
                eq("Lead Owner")
        );
    }

    @Test
    void updateTask_doesNotNotifyOrEmailWhenTaskHasNoAssignee() {
        Task task = task(TaskStatus.TODO);
        task.setAssignee(null);
        when(taskRepository.findById(900L)).thenReturn(Optional.of(task));

        Map<String, Object> updated = service.updateTask("kc-leader", 900L, taskUpdate(
                "Updated task",
                "Updated description",
                TaskPriority.MEDIUM,
                futureWorkingDay(0),
                futureWorkingDay(3)
        ));

        assertThat(updated.get("assigneeId")).isNull();
        verify(notificationEventProducer, never()).publish(any());
        verify(hrEmailService, never()).sendTaskUpdated(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateTask_rejectsPastStartDate() {
        Task task = task(TaskStatus.TODO);
        when(taskRepository.findById(900L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.updateTask("kc-leader", 900L, taskUpdate(
                "Updated task",
                "Updated description",
                TaskPriority.MEDIUM,
                LocalDate.now().minusDays(1),
                nextWorkingDay()
        )))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Start date cannot be in the past.");

        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    void updateTask_rejectsPastDueDate() {
        Task task = task(TaskStatus.TODO);
        when(taskRepository.findById(900L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.updateTask("kc-leader", 900L, taskUpdate(
                "Updated task",
                "Updated description",
                TaskPriority.MEDIUM,
                nextWorkingDay(),
                LocalDate.now().minusDays(1)
        )))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Due date cannot be in the past.");

        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    void updateTask_rejectsTaskOutsideLeaderTeam() {
        Task task = task(TaskStatus.TODO);
        Project otherProject = new Project();
        otherProject.setId(501L);
        otherProject.setName("Other Project");
        otherProject.setTeam(otherTeam());
        task.setProject(otherProject);
        when(taskRepository.findById(900L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.updateTask("kc-leader", 900L, taskUpdate(
                "Updated task",
                "Updated description",
                TaskPriority.MEDIUM,
                futureWorkingDay(0),
                futureWorkingDay(3)
        )))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Task does not belong to your team.");
    }

    @Test
    void updateTask_rejectsDueDateBeforeStartDate() {
        Task task = task(TaskStatus.TODO);
        when(taskRepository.findById(900L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.updateTask("kc-leader", 900L, taskUpdate(
                "Updated task",
                "Updated description",
                TaskPriority.MEDIUM,
                futureWorkingDay(3),
                futureWorkingDay(0)
        )))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Due date cannot be before start date.");

        verify(notificationEventProducer, never()).publish(any());
        verify(hrEmailService, never()).sendTaskUpdated(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateTask_rejectsWeekendTaskDate() {
        Task task = task(TaskStatus.TODO);
        LocalDate weekend = futureSaturday();
        when(taskRepository.findById(900L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.updateTask("kc-leader", 900L, taskUpdate(
                "Updated task",
                "Updated description",
                TaskPriority.MEDIUM,
                weekend,
                weekend.plusDays(5)
        )))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Start date falls on a weekend.");
    }

    @Test
    void updateTask_rejectsPublicHolidayTaskDate() {
        Task task = task(TaskStatus.TODO);
        LocalDate holiday = futureWorkingDay(2);
        when(taskRepository.findById(900L)).thenReturn(Optional.of(task));
        when(workingDayService.isWorkingDay(holiday)).thenReturn(false);
        when(workingDayService.isPublicHoliday(holiday)).thenReturn(true);

        assertThatThrownBy(() -> service.updateTask("kc-leader", 900L, taskUpdate(
                "Updated task",
                "Updated description",
                TaskPriority.MEDIUM,
                futureWorkingDay(0),
                holiday
        )))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Due date falls on a public holiday.");
    }

    @Test
    void updateTask_blocksWhenCurrentAssigneeHasNoAvailableWorkingDays() {
        Task task = task(TaskStatus.TODO);
        LocalDate start = futureWorkingDay(0);
        LocalDate end = futureWorkingDay(4);
        when(taskRepository.findById(900L)).thenReturn(Optional.of(task));
        when(leaveRequestRepository.findByUserIdAndDateRangeAndStatusIn(eq(20L), eq(start), eq(end), anyList()))
                .thenReturn(List.of(leave(tn.isetbizerte.pfe.hrbackend.common.enums.LeaveStatus.APPROVED, start, end)));

        assertThatThrownBy(() -> service.updateTask("kc-leader", 900L, taskUpdate(
                "Updated task",
                "Updated description",
                TaskPriority.MEDIUM,
                start,
                end
        )))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Employee has no available working days during this task period.");
    }

    @Test
    void updateTask_allowsWarningsWhenAvailabilityIsNotFullyBlocked() {
        Task task = task(TaskStatus.TODO);
        LocalDate start = futureFridayTaskStart();
        LocalDate end = start.plusDays(6);
        LocalDate approvedLeave = start.plusDays(3);
        LocalDate pendingLeave = start.plusDays(5);
        when(taskRepository.findById(900L)).thenReturn(Optional.of(task));
        when(leaveRequestRepository.findByUserIdAndDateRangeAndStatusIn(eq(20L), eq(start), eq(end), anyList()))
                .thenReturn(List.of(
                        leave(tn.isetbizerte.pfe.hrbackend.common.enums.LeaveStatus.APPROVED, approvedLeave, approvedLeave),
                        leave(tn.isetbizerte.pfe.hrbackend.common.enums.LeaveStatus.PENDING, pendingLeave, pendingLeave)
                ));

        Map<String, Object> updated = service.updateTask("kc-leader", 900L, taskUpdate(
                "Updated task",
                "Updated description",
                TaskPriority.HIGH,
                start,
                end
        ));

        assertThat(updated.get("title")).isEqualTo("Updated task");
        assertThat(updated.get("priority")).isEqualTo("HIGH");
        verify(taskRepository).save(task);
    }

    @Test
    void updateTaskStatus_allowsTodoToInProgress() {
        Task task = task(TaskStatus.TODO);
        when(taskRepository.findById(900L)).thenReturn(Optional.of(task));

        service.updateTaskStatus("kc-employee", 900L, TaskStatus.IN_PROGRESS);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        verify(taskRepository).save(task);
    }

    @Test
    void updateTaskStatus_allowsInProgressToDone() {
        Task task = task(TaskStatus.IN_PROGRESS);
        when(taskRepository.findById(900L)).thenReturn(Optional.of(task));

        service.updateTaskStatus("kc-employee", 900L, TaskStatus.DONE);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.DONE);
        verify(taskRepository).save(task);
    }

    @Test
    void updateTaskStatus_rejectsTodoToDone() {
        Task task = task(TaskStatus.TODO);
        when(taskRepository.findById(900L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.updateTaskStatus("kc-employee", 900L, TaskStatus.DONE))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Task must be started before it can be completed.");
    }

    @Test
    void updateTaskStatus_rejectsInProgressToTodo() {
        Task task = task(TaskStatus.IN_PROGRESS);
        when(taskRepository.findById(900L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.updateTaskStatus("kc-employee", 900L, TaskStatus.TODO))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Cannot move a task back to TODO once it is in progress.");
    }

    @Test
    void updateTaskStatus_rejectsDoneToInProgress() {
        Task task = task(TaskStatus.DONE);
        when(taskRepository.findById(900L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.updateTaskStatus("kc-employee", 900L, TaskStatus.IN_PROGRESS))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Task is already completed and cannot be changed.");
    }

    @Test
    void updateTaskStatus_rejectsNonAssignee() {
        Task task = task(TaskStatus.TODO);
        when(taskRepository.findById(900L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.updateTaskStatus("kc-other", 900L, TaskStatus.IN_PROGRESS))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("You can only update your own tasks.");
    }

    private void stubWorkingDays() {
        lenient().when(workingDayService.isWorkingDay(any(LocalDate.class))).thenAnswer(invocation -> {
            LocalDate date = invocation.getArgument(0);
            return date != null
                    && date.getDayOfWeek() != DayOfWeek.SATURDAY
                    && date.getDayOfWeek() != DayOfWeek.SUNDAY;
        });
        lenient().when(workingDayService.countWorkingDays(any(LocalDate.class), any(LocalDate.class))).thenAnswer(invocation -> {
            LocalDate start = invocation.getArgument(0);
            LocalDate end = invocation.getArgument(1);
            int count = 0;
            LocalDate current = start;
            while (!current.isAfter(end)) {
                if (current.getDayOfWeek() != DayOfWeek.SATURDAY
                        && current.getDayOfWeek() != DayOfWeek.SUNDAY) {
                    count++;
                }
                current = current.plusDays(1);
            }
            return count;
        });
        lenient().when(workingDayService.isPublicHoliday(any(LocalDate.class))).thenReturn(false);
    }

    private CreateTaskRequest taskRequest(LocalDate start, LocalDate due) {
        CreateTaskRequest request = new CreateTaskRequest();
        request.setTitle("Prepare release");
        request.setDescription("Prepare payroll release notes");
        request.setPriority(TaskPriority.HIGH);
        request.setStartDate(start);
        request.setDueDate(due);
        request.setAssignmentMode(TaskAssignmentMode.ONE);
        request.setAssigneeId(20L);
        return request;
    }

    private CreateProjectRequest projectCreate(String name, String description) {
        CreateProjectRequest request = new CreateProjectRequest();
        request.setName(name);
        request.setDescription(description);
        return request;
    }

    private UpdateProjectRequest projectUpdate(String name, String description) {
        UpdateProjectRequest request = new UpdateProjectRequest();
        request.setName(name);
        request.setDescription(description);
        return request;
    }

    private UpdateTaskRequest taskUpdate(String title, String description, TaskPriority priority, LocalDate start, LocalDate due) {
        UpdateTaskRequest request = new UpdateTaskRequest();
        request.setTitle(title);
        request.setDescription(description);
        request.setPriority(priority);
        request.setStartDate(start);
        request.setDueDate(due);
        return request;
    }

    private LocalDate nextWorkingDay() {
        return futureWorkingDay(0);
    }

    private LocalDate futureWorkingDay(int offset) {
        LocalDate date = LocalDate.now().plusDays(1);
        int remaining = offset;
        while (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            date = date.plusDays(1);
        }
        while (remaining > 0) {
            date = date.plusDays(1);
            if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                remaining--;
            }
        }
        return date;
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

    private Task task(TaskStatus status) {
        Task task = new Task();
        task.setId(900L);
        task.setTitle("Prepare release");
        task.setDescription("Prepare payroll release notes");
        task.setPriority(TaskPriority.HIGH);
        task.setStatus(status);
        task.setStartDate(futureWorkingDay(0));
        task.setDueDate(futureWorkingDay(3));
        task.setProject(project);
        task.setAssignee(assignee);
        return task;
    }

    private tn.isetbizerte.pfe.hrbackend.modules.employee.entity.LeaveRequest leave(
            tn.isetbizerte.pfe.hrbackend.common.enums.LeaveStatus status,
            LocalDate start,
            LocalDate end) {
        tn.isetbizerte.pfe.hrbackend.modules.employee.entity.LeaveRequest leave =
                new tn.isetbizerte.pfe.hrbackend.modules.employee.entity.LeaveRequest();
        leave.setUser(assignee);
        leave.setLeaveType(tn.isetbizerte.pfe.hrbackend.common.enums.LeaveType.ANNUAL);
        leave.setStatus(status);
        leave.setStartDate(start);
        leave.setEndDate(end);
        leave.setNumberOfDays(1);
        return leave;
    }

    private Team otherTeam() {
        Team otherTeam = new Team();
        otherTeam.setId(200L);
        otherTeam.setName("Support");
        return otherTeam;
    }

    private User user(Long id, String keycloakId, String username, TypeRole role) {
        User user = new User(keycloakId, username);
        user.setId(id);
        user.setRole(role);
        user.setActive(true);
        return user;
    }

    private Person person(String firstName, String lastName, String email) {
        Person person = new Person();
        person.setFirstName(firstName);
        person.setLastName(lastName);
        person.setEmail(email);
        return person;
    }
}
