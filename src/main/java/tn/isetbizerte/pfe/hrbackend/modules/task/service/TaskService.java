package tn.isetbizerte.pfe.hrbackend.modules.task.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.isetbizerte.pfe.hrbackend.common.enums.TaskStatus;
import tn.isetbizerte.pfe.hrbackend.common.event.NotificationEvent;
import tn.isetbizerte.pfe.hrbackend.common.exception.BadRequestException;
import tn.isetbizerte.pfe.hrbackend.common.exception.ResourceNotFoundException;
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
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Service
public class TaskService {

    private static final Logger logger = LoggerFactory.getLogger(TaskService.class);

    private final ProjectRepository projectRepository;
    private final TaskRepository    taskRepository;
    private final TeamRepository    teamRepository;
    private final UserRepository    userRepository;
    private final TaskAssignmentPolicy taskAssignmentPolicy;
    private final NotificationEventProducer notificationEventProducer;
    private final HREmailService hrEmailService;
    private final NotificationService notificationService;

    @Autowired
    public TaskService(ProjectRepository projectRepository, TaskRepository taskRepository,
                       TeamRepository teamRepository, UserRepository userRepository,
                       TaskAssignmentPolicy taskAssignmentPolicy,
                       NotificationEventProducer notificationEventProducer,
                       HREmailService hrEmailService,
                       NotificationService notificationService) {
        this.projectRepository = projectRepository;
        this.taskRepository    = taskRepository;
        this.teamRepository    = teamRepository;
        this.userRepository    = userRepository;
        this.taskAssignmentPolicy = taskAssignmentPolicy;
        this.notificationEventProducer = notificationEventProducer;
        this.hrEmailService = hrEmailService;
        this.notificationService = notificationService;
    }

    public TaskService(ProjectRepository projectRepository, TaskRepository taskRepository,
                       TeamRepository teamRepository, UserRepository userRepository,
                       LeaveRequestRepository leaveRequestRepository,
                       AuthorizationRequestRepository authorizationRequestRepository,
                       WorkingDayService workingDayService,
                       NotificationEventProducer notificationEventProducer,
                       HREmailService hrEmailService,
                       NotificationService notificationService) {
        this(
                projectRepository,
                taskRepository,
                teamRepository,
                userRepository,
                new TaskAssignmentPolicy(
                        projectRepository,
                        teamRepository,
                        userRepository,
                        leaveRequestRepository,
                        authorizationRequestRepository,
                        workingDayService
                ),
                notificationEventProducer,
                hrEmailService,
                notificationService
        );
    }

    // ─────────────────────────────────────────────────────────────
    // TEAM LEADER — PROJECTS
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> createProject(String leaderKeycloakId, CreateProjectRequest req) {
        Team team = getTeamByLeader(leaderKeycloakId);
        String projectName = normalizeProjectName(req.getName());
        if (projectRepository.existsByTeamIdAndNameIgnoreCase(team.getId(), projectName)) {
            throw new BadRequestException("A project with this name already exists in your team.");
        }
        Project project = new Project();
        project.setName(projectName);
        project.setDescription(req.getDescription());
        project.setTeam(team);
        projectRepository.save(project);
        logger.info("Project '{}' created for team '{}'", project.getName(), team.getName());
        return mapProject(project);
    }

    public List<Map<String, Object>> getMyProjects(String leaderKeycloakId) {
        Team team = getTeamByLeader(leaderKeycloakId);
        return projectRepository.findByTeamIdWithTasks(team.getId())
                .stream().map(this::mapProject).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> updateProject(String leaderKeycloakId, Long projectId, UpdateProjectRequest req) {
        Team team = getTeamByLeader(leaderKeycloakId);
        Project project = getProjectInTeam(projectId, team);
        String projectName = normalizeProjectName(req.getName());
        if (projectRepository.existsByTeamIdAndNameIgnoreCaseAndIdNot(team.getId(), projectName, project.getId())) {
            throw new BadRequestException("A project with this name already exists in your team.");
        }
        project.setName(projectName);
        project.setDescription(req.getDescription());
        project.setUpdatedAt(LocalDateTime.now());
        projectRepository.save(project);
        logger.info("Project '{}' updated for team '{}'", project.getName(), team.getName());
        return mapProject(project);
    }

    @Transactional
    public Map<String, Object> deleteProject(String leaderKeycloakId, Long projectId) {
        Team team = getTeamByLeader(leaderKeycloakId);
        Project project = getProjectInTeam(projectId, team);
        projectRepository.delete(project);
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("message", "Project deleted");
        return res;
    }

    // ─────────────────────────────────────────────────────────────
    // TEAM LEADER — TASKS
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> createTask(String leaderKeycloakId, Long projectId, CreateTaskRequest req) {
        TaskAssignmentPolicy.TaskAssignmentContext context = taskAssignmentPolicy.buildAssignmentContext(leaderKeycloakId, projectId, req, true);
        List<Map<String, Object>> createdTasks = new ArrayList<>();

        for (User assignee : context.preview.creatableAssignees) {
            Task task = new Task();
            task.setTitle(req.getTitle());
            task.setDescription(req.getDescription());
            task.setPriority(req.getPriority());
            task.setStartDate(req.getStartDate());
            task.setDueDate(req.getDueDate());
            task.setProject(context.project);
            task.setAssignee(assignee);
            taskRepository.save(task);
            notifyAssigneeOfTaskAssignment(task, assignee, leaderKeycloakId);
            createdTasks.add(mapTask(task));
            logger.info("Task '{}' created in project '{}' for assignee={}", task.getTitle(), context.project.getName(), assignee.getId());
        }

        Map<String, Object> preview = context.preview.toMap();
        preview.put("createdTasks", createdTasks);
        preview.put("createdCount", createdTasks.size());
        preview.put("requestedCount", context.requestedAssignees.size());
        preview.put("mode", req.getAssignmentMode().name());
        preview.put("projectId", context.project.getId());
        preview.put("projectName", context.project.getName());
        preview.put("message", buildCreateMessage(createdTasks.size(), context.preview.blocked.size(), context.preview.warned.size()));
        return preview;
    }

    public Map<String, Object> previewTaskAssignment(String leaderKeycloakId, Long projectId, CreateTaskRequest req) {
        return taskAssignmentPolicy.buildAssignmentContext(leaderKeycloakId, projectId, req, false).preview.toMap();
    }

    @Transactional
    public Map<String, Object> updateTask(String leaderKeycloakId, Long taskId, UpdateTaskRequest req) {
        Team team = getTeamByLeader(leaderKeycloakId);
        Task task = getTaskInTeam(taskId, team);

        taskAssignmentPolicy.validateTaskUpdateAvailability(task.getAssignee(), req.getStartDate(), req.getDueDate());

        task.setTitle(req.getTitle());
        task.setDescription(req.getDescription());
        task.setPriority(req.getPriority());
        task.setStartDate(req.getStartDate());
        task.setDueDate(req.getDueDate());
        task.setUpdatedAt(LocalDateTime.now());
        taskRepository.save(task);
        notifyAssigneeOfTaskUpdate(task, leaderKeycloakId);
        logger.info("Task '{}' updated in project '{}'", task.getTitle(), task.getProject().getName());
        return mapTask(task);
    }

    @Transactional
    public Map<String, Object> deleteTask(String leaderKeycloakId, Long taskId) {
        Team team = getTeamByLeader(leaderKeycloakId);
        Task task = getTaskInTeam(taskId, team);
        taskRepository.delete(task);
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("message", "Task deleted");
        return res;
    }

    // ─────────────────────────────────────────────────────────────
    // EMPLOYEE — view and update own tasks
    // ─────────────────────────────────────────────────────────────

    public List<Map<String, Object>> getMyTasks(String userIdentifier) {
        User user = resolveUser(userIdentifier);
        return taskRepository.findByAssigneeId(user.getId())
                .stream().map(this::mapTask).collect(Collectors.toList());
    }

    public Map<String, Object> getMyTaskById(String userIdentifier, Long taskId) {
        User user = resolveUser(userIdentifier);
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));

        if (task.getAssignee() == null || !task.getAssignee().getId().equals(user.getId())) {
            throw new UnauthorizedException("You can only view your own tasks.");
        }

        return mapTask(task);
    }

    @Transactional
    public Map<String, Object> updateTaskStatus(String userIdentifier, Long taskId, TaskStatus newStatus) {
        User user = resolveUser(userIdentifier);
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
        if (task.getAssignee() == null || !task.getAssignee().getId().equals(user.getId()))
            throw new UnauthorizedException("You can only update your own tasks.");

        // Validate forward-only transitions: TODO → IN_PROGRESS → DONE
        TaskStatus current = task.getStatus();
        if (current == TaskStatus.DONE) {
            throw new BadRequestException("Task is already completed and cannot be changed.");
        }
        if (current == TaskStatus.TODO && newStatus == TaskStatus.DONE) {
            throw new BadRequestException("Task must be started before it can be completed.");
        }
        if (current == TaskStatus.IN_PROGRESS && newStatus == TaskStatus.TODO) {
            throw new BadRequestException("Cannot move a task back to TODO once it is in progress.");
        }

        task.setStatus(newStatus);
        task.setUpdatedAt(LocalDateTime.now());
        taskRepository.save(task);

        // Notify Team Leader when a team member completes a task.
        if (newStatus == TaskStatus.DONE) {
            try {
                Team team = task.getProject() != null ? task.getProject().getTeam() : null;
                User leader = team != null ? team.getTeamLeader() : null;
                if (leader != null && leader.getKeycloakId() != null && !leader.getKeycloakId().isBlank()) {
                    String msg = String.format(
                            "Task completed: %s (%s) by %s",
                            task.getTitle(),
                            task.getProject() != null ? task.getProject().getName() : "No project",
                            task.getAssigneeFullName()
                    );
                    try {
                        notificationEventProducer.publish(new NotificationEvent(
                                leader.getKeycloakId(),
                                msg,
                                "TASK_DONE",
                                "TASK",
                                task.getId(),
                                "/team/tasks"
                        ));
                    } catch (Exception e) {
                        logger.warn("Failed to publish TASK_DONE notification event, falling back to direct persistence. taskId={}", taskId, e);
                        notificationService.createNotification(
                                leader.getKeycloakId(),
                                msg,
                                "TASK_DONE",
                                "TASK",
                                task.getId(),
                                "/team/tasks"
                        );
                    }

                    try {
                        if (leader.getPerson() != null
                                && leader.getPerson().getEmail() != null
                                && !leader.getPerson().getEmail().isBlank()) {
                            hrEmailService.sendTaskCompletedToLeader(
                                    leader.getPerson().getEmail(),
                                    leader.getPerson().getFirstName(),
                                    leader.getPerson().getLastName(),
                                    task.getTitle(),
                                    task.getProject() != null ? task.getProject().getName() : null,
                                    task.getAssigneeFullName()
                            );
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to email leader on task completion taskId={}", taskId, e);
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to notify leader on task completion taskId={}", taskId, e);
            }
        }

        return mapTask(task);
    }

    // ─────────────────────────────────────────────────────────────
    // INTERNAL HELPERS
    // ─────────────────────────────────────────────────────────────

    private Team getTeamByLeader(String keycloakId) {
        return teamRepository.findByTeamLeaderKeycloakId(keycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("No team assigned to this Team Leader."));
    }

    private User resolveUser(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new ResourceNotFoundException("User not found");
        }
        return userRepository.findByKeycloakId(identifier)
                .or(() -> userRepository.findByUsername(identifier))
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + identifier));
    }

    private Project getProjectInTeam(Long projectId, Team team) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
        if (project.getTeam() == null || !project.getTeam().getId().equals(team.getId())) {
            throw new UnauthorizedException("This project does not belong to your team.");
        }
        return project;
    }

    private Task getTaskInTeam(Long taskId, Team team) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
        if (task.getProject() == null
                || task.getProject().getTeam() == null
                || !task.getProject().getTeam().getId().equals(team.getId())) {
            throw new UnauthorizedException("Task does not belong to your team.");
        }
        return task;
    }

    private String normalizeProjectName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new BadRequestException("Project name is required");
        }
        return name.trim();
    }

    private void notifyAssigneeOfTaskAssignment(Task task, User assignee, String leaderKeycloakId) {
        String assigneeKeycloakId = assignee.getKeycloakId();
        String leaderName = resolveLeaderName(leaderKeycloakId);

        String notificationMessage = String.format(
                "New task assigned: %s (Project: %s, Priority: %s, Start: %s, Due: %s)",
                task.getTitle(),
                task.getProject().getName(),
                task.getPriority().name(),
                task.getStartDate() != null ? task.getStartDate().toString() : "No start date",
                task.getDueDate() != null ? task.getDueDate().toString() : "No due date"
        );

        try {
            notificationEventProducer.publish(
                    new NotificationEvent(
                            assigneeKeycloakId,
                            notificationMessage,
                            "TASK_ASSIGNED",
                            "TASK",
                            task.getId(),
                            "/employee/tasks?taskId=" + task.getId()
                    )
            );
            logger.info("Task assignment notification sent for taskId={} to userId={}",
                    task.getId(), assigneeKeycloakId);
        } catch (Exception e) {
            logger.error("Failed to publish task assignment notification for taskId={}",
                    task.getId(), e);
            // Fallback to direct persistence when Kafka is unavailable
            try {
                notificationService.createNotification(
                        assigneeKeycloakId,
                        notificationMessage,
                        "TASK_ASSIGNED",
                        "TASK",
                        task.getId(),
                        "/employee/tasks?taskId=" + task.getId()
                );
                logger.info("Task assignment notification persisted directly for taskId={} to userId={}",
                        task.getId(), assigneeKeycloakId);
            } catch (Exception fallbackEx) {
                logger.error("Failed direct notification fallback for taskId={}", task.getId(), fallbackEx);
            }
        }

        try {
            if (assignee.getPerson() != null && assignee.getPerson().getEmail() != null
                    && !assignee.getPerson().getEmail().isBlank()) {
                hrEmailService.sendTaskAssigned(
                        assignee.getPerson().getEmail(),
                        assignee.getPerson().getFirstName(),
                        assignee.getPerson().getLastName(),
                        task.getTitle(),
                        task.getDescription(),
                        task.getProject().getName(),
                        task.getPriority().name(),
                        task.getStartDate(),
                        task.getDueDate(),
                        leaderName
                );
                logger.info("Task assignment email sent for taskId={} to {}",
                        task.getId(), assignee.getPerson().getEmail());
            } else {
                logger.warn("Task assignment email skipped (missing assignee email) for taskId={}",
                        task.getId());
            }
        } catch (Exception e) {
            logger.error("Failed to send task assignment email for taskId={}", task.getId(), e);
        }
    }

    private void notifyAssigneeOfTaskUpdate(Task task, String leaderKeycloakId) {
        User assignee = task.getAssignee();
        if (assignee == null) {
            return;
        }

        String assigneeKeycloakId = assignee.getKeycloakId();
        String leaderName = resolveLeaderName(leaderKeycloakId);
        String notificationMessage = "Task updated: " + task.getTitle();

        try {
            notificationEventProducer.publish(
                    new NotificationEvent(
                            assigneeKeycloakId,
                            notificationMessage,
                            "TASK_UPDATED",
                            "TASK",
                            task.getId(),
                            "/employee/tasks?taskId=" + task.getId()
                    )
            );
            logger.info("Task update notification enqueued for taskId={} to userId={}",
                    task.getId(), assigneeKeycloakId);
        } catch (Exception e) {
            logger.warn("Failed to enqueue task update notification for taskId={}",
                    task.getId(), e);
        }

        try {
            if (assignee.getPerson() != null && assignee.getPerson().getEmail() != null
                    && !assignee.getPerson().getEmail().isBlank()) {
                hrEmailService.sendTaskUpdated(
                        assignee.getPerson().getEmail(),
                        assignee.getPerson().getFirstName(),
                        assignee.getPerson().getLastName(),
                        task.getTitle(),
                        task.getDescription(),
                        task.getProject() != null ? task.getProject().getName() : null,
                        task.getPriority() != null ? task.getPriority().name() : null,
                        task.getStartDate(),
                        task.getDueDate(),
                        leaderName
                );
                logger.info("Task update email sent for taskId={} to {}",
                        task.getId(), assignee.getPerson().getEmail());
            } else {
                logger.warn("Task update email skipped (missing assignee email) for taskId={}",
                        task.getId());
            }
        } catch (Exception e) {
            logger.warn("Failed to send task update email for taskId={}", task.getId(), e);
        }
    }

    private String resolveLeaderName(String leaderKeycloakId) {
        return userRepository.findByKeycloakId(leaderKeycloakId)
                .map(leader -> {
                    if (leader.getPerson() != null) {
                        return leader.getPerson().getFirstName() + " " + leader.getPerson().getLastName();
                    }
                    return leader.getUsername();
                })
                .orElse("Team Leader");
    }

    private String buildCreateMessage(int createdCount, int blockedCount, int warnedCount) {
        if (createdCount == 0) return "No tasks created.";
        String base = createdCount == 1 ? "1 task created." : createdCount + " tasks created.";
        if (blockedCount > 0 || warnedCount > 0) {
            return String.format("%s %d blocked, %d warned.", base, blockedCount, warnedCount);
        }
        return base;
    }

    private Map<String, Object> mapProject(Project p) {
        Map<String, Object> m = new HashMap<>();
        m.put("id",          p.getId());
        m.put("name",        p.getName());
        m.put("description", p.getDescription());
        m.put("teamId",      p.getTeam().getId());
        m.put("teamName",    p.getTeam().getName());
        List tasks = p.getTasks() != null ? p.getTasks() : java.util.List.of();
        m.put("taskCount",   tasks.size());
        m.put("tasks", ((java.util.List<Task>) tasks).stream().map(this::mapTask).collect(Collectors.toList()));
        m.put("createdAt",   p.getCreatedAt());
        return m;
    }

    private Map<String, Object> mapTask(Task t) {
        Project project = t.getProject();
        Map<String, Object> m = new HashMap<>();
        m.put("id",           t.getId());
        m.put("title",        t.getTitle());
        m.put("description",  t.getDescription());
        m.put("status",       t.getStatus().name());
        m.put("priority",     t.getPriority().name());
        m.put("startDate",   t.getStartDate());
        m.put("dueDate",      t.getDueDate());
        m.put("projectId",    project != null ? project.getId() : null);
        m.put("projectName",  project != null ? project.getName() : null);
        m.put("projectDescription", project != null ? project.getDescription() : null);
        m.put("assigneeId",   t.getAssignee() != null ? t.getAssignee().getId() : null);
        m.put("assigneeName", t.getAssigneeFullName());
        m.put("createdAt",    t.getCreatedAt());
        m.put("updatedAt",    t.getUpdatedAt());
        return m;
    }

}
