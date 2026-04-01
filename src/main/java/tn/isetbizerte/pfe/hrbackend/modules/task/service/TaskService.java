package tn.isetbizerte.pfe.hrbackend.modules.task.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.isetbizerte.pfe.hrbackend.common.enums.TaskStatus;
import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;
import tn.isetbizerte.pfe.hrbackend.common.event.NotificationEvent;
import tn.isetbizerte.pfe.hrbackend.common.exception.BadRequestException;
import tn.isetbizerte.pfe.hrbackend.common.exception.ResourceNotFoundException;
import tn.isetbizerte.pfe.hrbackend.common.exception.UnauthorizedException;
import tn.isetbizerte.pfe.hrbackend.infrastructure.email.HREmailService;
import tn.isetbizerte.pfe.hrbackend.infrastructure.kafka.producer.NotificationEventProducer;
import tn.isetbizerte.pfe.hrbackend.modules.notification.service.NotificationService;
import tn.isetbizerte.pfe.hrbackend.modules.task.dto.CreateProjectRequest;
import tn.isetbizerte.pfe.hrbackend.modules.task.dto.CreateTaskRequest;
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
import java.util.stream.Collectors;

@Service
public class TaskService {

    private static final Logger logger = LoggerFactory.getLogger(TaskService.class);

    private final ProjectRepository projectRepository;
    private final TaskRepository    taskRepository;
    private final TeamRepository    teamRepository;
    private final UserRepository    userRepository;
    private final NotificationEventProducer notificationEventProducer;
    private final HREmailService hrEmailService;
    private final NotificationService notificationService;

    public TaskService(ProjectRepository projectRepository, TaskRepository taskRepository,
                       TeamRepository teamRepository, UserRepository userRepository,
                       NotificationEventProducer notificationEventProducer,
                       HREmailService hrEmailService,
                       NotificationService notificationService) {
        this.projectRepository = projectRepository;
        this.taskRepository    = taskRepository;
        this.teamRepository    = teamRepository;
        this.userRepository    = userRepository;
        this.notificationEventProducer = notificationEventProducer;
        this.hrEmailService = hrEmailService;
        this.notificationService = notificationService;
    }

    // ─────────────────────────────────────────────────────────────
    // TEAM LEADER — PROJECTS
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> createProject(String leaderKeycloakId, CreateProjectRequest req) {
        Team team = getTeamByLeader(leaderKeycloakId);
        Project project = new Project();
        project.setName(req.getName());
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
    public Map<String, Object> deleteProject(String leaderKeycloakId, Long projectId) {
        Team team = getTeamByLeader(leaderKeycloakId);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
        if (!project.getTeam().getId().equals(team.getId()))
            throw new UnauthorizedException("This project does not belong to your team.");
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
        Team team = getTeamByLeader(leaderKeycloakId);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
        if (!project.getTeam().getId().equals(team.getId()))
            throw new UnauthorizedException("Project does not belong to your team.");

        User assignee = userRepository.findById(req.getAssigneeId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + req.getAssigneeId()));

        // Assignee must be in the same team (or the TL themselves)
        boolean isMember = (assignee.getTeam() != null && assignee.getTeam().getId().equals(team.getId()))
                        || (assignee.getRole() == TypeRole.TEAM_LEADER && assignee.getKeycloakId().equals(leaderKeycloakId));
        if (!isMember)
            throw new BadRequestException("Assignee is not a member of your team.");

        Task task = new Task();
        task.setTitle(req.getTitle());
        task.setDescription(req.getDescription());
        task.setPriority(req.getPriority());
        task.setStartDate(req.getStartDate());
        task.setDueDate(req.getDueDate());
        task.setProject(project);
        task.setAssignee(assignee);
        taskRepository.save(task);
        notifyAssigneeOfTaskAssignment(task, assignee, leaderKeycloakId);
        logger.info("Task '{}' created in project '{}'", task.getTitle(), project.getName());
        return mapTask(task);
    }

    @Transactional
    public Map<String, Object> deleteTask(String leaderKeycloakId, Long taskId) {
        Team team = getTeamByLeader(leaderKeycloakId);
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
        if (!task.getProject().getTeam().getId().equals(team.getId()))
            throw new UnauthorizedException("Task does not belong to your team.");
        taskRepository.delete(task);
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("message", "Task deleted");
        return res;
    }

    // ─────────────────────────────────────────────────────────────
    // EMPLOYEE — view and update own tasks
    // ─────────────────────────────────────────────────────────────

    public List<Map<String, Object>> getMyTasks(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        return taskRepository.findByAssigneeId(user.getId())
                .stream().map(this::mapTask).collect(Collectors.toList());
    }

    public Map<String, Object> getMyTaskById(String username, Long taskId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));

        if (task.getAssignee() == null || !task.getAssignee().getId().equals(user.getId())) {
            throw new UnauthorizedException("You can only view your own tasks.");
        }

        return mapTask(task);
    }

    @Transactional
    public Map<String, Object> updateTaskStatus(String username, Long taskId, TaskStatus newStatus) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
        if (task.getAssignee() == null || !task.getAssignee().getId().equals(user.getId()))
            throw new UnauthorizedException("You can only update your own tasks.");

        // Validate forward-only transitions: TODO → IN_PROGRESS → DONE
        TaskStatus current = task.getStatus();
        if (current == TaskStatus.DONE) {
            throw new BadRequestException("Task is already completed and cannot be changed.");
        }
        if (current == TaskStatus.IN_PROGRESS && newStatus == TaskStatus.TODO) {
            throw new BadRequestException("Cannot move a task back to TODO once it is in progress.");
        }

        task.setStatus(newStatus);
        task.setUpdatedAt(LocalDateTime.now());
        taskRepository.save(task);
        return mapTask(task);
    }

    // ─────────────────────────────────────────────────────────────
    // INTERNAL HELPERS
    // ─────────────────────────────────────────────────────────────

    private Team getTeamByLeader(String keycloakId) {
        return teamRepository.findByTeamLeaderKeycloakId(keycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("No team assigned to this Team Leader."));
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
                        task.getProject().getName(),
                        task.getDescription(),
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
        Map<String, Object> m = new HashMap<>();
        m.put("id",           t.getId());
        m.put("title",        t.getTitle());
        m.put("description",  t.getDescription());
        m.put("status",       t.getStatus().name());
        m.put("priority",     t.getPriority().name());
        m.put("startDate",   t.getStartDate());
        m.put("dueDate",      t.getDueDate());
        m.put("projectId",    t.getProject().getId());
        m.put("projectName",  t.getProject().getName());
        m.put("assigneeId",   t.getAssignee() != null ? t.getAssignee().getId() : null);
        m.put("assigneeName", t.getAssigneeFullName());
        m.put("createdAt",    t.getCreatedAt());
        m.put("updatedAt",    t.getUpdatedAt());
        return m;
    }
}
