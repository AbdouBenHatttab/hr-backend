package tn.isetbizerte.pfe.hrbackend.modules.task.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tn.isetbizerte.pfe.hrbackend.common.enums.TaskStatus;
import tn.isetbizerte.pfe.hrbackend.modules.notification.repository.NotificationRepository;
import tn.isetbizerte.pfe.hrbackend.modules.notification.service.NotificationService;
import tn.isetbizerte.pfe.hrbackend.modules.task.entity.Task;
import tn.isetbizerte.pfe.hrbackend.modules.task.repository.TaskRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Sends Team Leader reminders when tasks are near due date and still TODO (no progress).
 * Runs hourly; deduplicated per (leader, task) per day.
 */
@Component
public class TaskReminderScheduler {

    private static final Logger logger = LoggerFactory.getLogger(TaskReminderScheduler.class);
    private static final int LOOKAHEAD_DAYS = 2;

    private final TaskRepository taskRepository;
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;

    public TaskReminderScheduler(TaskRepository taskRepository,
                                 NotificationService notificationService,
                                 NotificationRepository notificationRepository) {
        this.taskRepository = taskRepository;
        this.notificationService = notificationService;
        this.notificationRepository = notificationRepository;
    }

    @Scheduled(cron = "0 0 * * * *") // every hour
    @Transactional
    public void remindNearDeadlineTodoTasks() {
        LocalDate today = LocalDate.now();
        LocalDate to = today.plusDays(LOOKAHEAD_DAYS);
        List<Task> dueSoon = taskRepository.findDueBetweenWithLeaderStatuses(
                List.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS),
                today,
                to
        );
        if (dueSoon.isEmpty()) return;

        LocalDateTime dayStart = today.atStartOfDay();
        for (Task task : dueSoon) {
            try {
                User leader = task.getProject().getTeam().getTeamLeader();
                if (leader == null || leader.getKeycloakId() == null || leader.getKeycloakId().isBlank()) continue;

                boolean alreadySentToday = notificationRepository.existsByUserAndTypeAndReferenceTypeAndReferenceIdAndCreatedAtAfter(
                        leader,
                        "TASK_DEADLINE_RISK",
                        "TASK",
                        task.getId(),
                        dayStart
                );
                if (alreadySentToday) continue;

                long daysLeft = ChronoUnit.DAYS.between(today, task.getDueDate());
                String when = daysLeft <= 0 ? "today" : ("in " + daysLeft + " day" + (daysLeft == 1 ? "" : "s"));

                String message = String.format(
                        "Task near deadline (%s) and not finished (%s): %s (%s) — assignee: %s",
                        when,
                        task.getStatus().name(),
                        task.getTitle(),
                        task.getProject().getName(),
                        task.getAssigneeFullName()
                );

                notificationService.createNotification(
                        leader.getKeycloakId(),
                        message,
                        "TASK_DEADLINE_RISK",
                        "TASK",
                        task.getId(),
                        "/team/tasks"
                );
            } catch (Exception e) {
                logger.warn("Failed deadline reminder for taskId={}", task.getId(), e);
            }
        }
    }
}
