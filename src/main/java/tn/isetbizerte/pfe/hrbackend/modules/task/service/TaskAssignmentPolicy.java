package tn.isetbizerte.pfe.hrbackend.modules.task.service;

import org.springframework.stereotype.Service;
import tn.isetbizerte.pfe.hrbackend.common.enums.AuthorizationType;
import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveStatus;
import tn.isetbizerte.pfe.hrbackend.common.enums.RequestStatus;
import tn.isetbizerte.pfe.hrbackend.common.enums.TaskAssignmentMode;
import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;
import tn.isetbizerte.pfe.hrbackend.common.exception.BadRequestException;
import tn.isetbizerte.pfe.hrbackend.common.exception.ResourceNotFoundException;
import tn.isetbizerte.pfe.hrbackend.common.exception.UnauthorizedException;
import tn.isetbizerte.pfe.hrbackend.modules.calendar.service.WorkingDayService;
import tn.isetbizerte.pfe.hrbackend.modules.employee.entity.LeaveRequest;
import tn.isetbizerte.pfe.hrbackend.modules.employee.repository.LeaveRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.requests.entity.AuthorizationRequest;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.AuthorizationRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.task.dto.CreateTaskRequest;
import tn.isetbizerte.pfe.hrbackend.modules.task.entity.Project;
import tn.isetbizerte.pfe.hrbackend.modules.task.repository.ProjectRepository;
import tn.isetbizerte.pfe.hrbackend.modules.team.entity.Team;
import tn.isetbizerte.pfe.hrbackend.modules.team.repository.TeamRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Owns task assignment eligibility and preview rules before TaskService creates or updates tasks.
 */
@Service
public class TaskAssignmentPolicy {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final ProjectRepository projectRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final AuthorizationRequestRepository authorizationRequestRepository;
    private final WorkingDayService workingDayService;

    public TaskAssignmentPolicy(ProjectRepository projectRepository,
                                TeamRepository teamRepository,
                                UserRepository userRepository,
                                LeaveRequestRepository leaveRequestRepository,
                                AuthorizationRequestRepository authorizationRequestRepository,
                                WorkingDayService workingDayService) {
        this.projectRepository = projectRepository;
        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.authorizationRequestRepository = authorizationRequestRepository;
        this.workingDayService = workingDayService;
    }

    TaskAssignmentContext buildAssignmentContext(String leaderKeycloakId, Long projectId, CreateTaskRequest req, boolean requireCreatableAssignee) {
        Team team = getTeamByLeader(leaderKeycloakId);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
        if (!project.getTeam().getId().equals(team.getId())) {
            throw new UnauthorizedException("Project does not belong to your team.");
        }

        LocalDate taskStart = req.getStartDate() != null ? req.getStartDate() : req.getDueDate();
        LocalDate taskEnd = req.getDueDate() != null ? req.getDueDate() : req.getStartDate();
        List<String> pastDateIssues = collectPastDateIssues(req.getStartDate(), req.getDueDate());
        if (!pastDateIssues.isEmpty()) {
            throw new BadRequestException(pastDateIssues.get(0));
        }
        if (taskStart != null && taskEnd != null && taskEnd.isBefore(taskStart)) {
            throw new BadRequestException("Due date cannot be before start date.");
        }

        List<User> requestedAssignees = resolveRequestedAssignees(team, leaderKeycloakId, req);
        AssignmentPreview preview = previewAssignments(requestedAssignees, taskStart, taskEnd);

        if (requireCreatableAssignee && preview.creatableAssignees.isEmpty()) {
            throw new BadRequestException("No eligible assignees available for this task. Resolve blocked assignment conflicts first.");
        }

        return new TaskAssignmentContext(team, project, requestedAssignees, preview);
    }

    void validateTaskUpdateAvailability(User assignee, LocalDate taskStart, LocalDate taskEnd) {
        List<String> pastDateIssues = collectPastDateIssues(taskStart, taskEnd);
        if (!pastDateIssues.isEmpty()) {
            throw new BadRequestException(pastDateIssues.get(0));
        }
        if (taskEnd.isBefore(taskStart)) {
            throw new BadRequestException("Due date cannot be before start date.");
        }

        if (assignee != null) {
            AssignmentPreview preview = previewAssignments(List.of(assignee), taskStart, taskEnd);
            if (!preview.blocked.isEmpty()) {
                throw new BadRequestException(firstBlockingReason(preview));
            }
        } else {
            List<String> dateIssues = collectDateIssues(taskStart, taskEnd);
            if (!dateIssues.isEmpty()) {
                throw new BadRequestException(dateIssues.get(0));
            }
        }
    }

    private Team getTeamByLeader(String keycloakId) {
        return teamRepository.findByTeamLeaderKeycloakId(keycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("No team assigned to this Team Leader."));
    }

    private List<User> resolveRequestedAssignees(Team team, String leaderKeycloakId, CreateTaskRequest req) {
        TaskAssignmentMode mode = req.getAssignmentMode() != null ? req.getAssignmentMode() : TaskAssignmentMode.ONE;

        if (mode == TaskAssignmentMode.ALL) {
            List<User> assignable = new ArrayList<>();
            if (team.getMembers() != null) assignable.addAll(team.getMembers());
            if (team.getTeamLeader() != null && leaderKeycloakId.equals(team.getTeamLeader().getKeycloakId())) {
                assignable.add(team.getTeamLeader());
            }
            return dedupeUsers(assignable);
        }

        if (mode == TaskAssignmentMode.SELECTED) {
            List<Long> ids = req.getAssigneeIds() != null ? req.getAssigneeIds() : List.of();
            if (ids.isEmpty()) {
                throw new BadRequestException("Select at least one assignee.");
            }
            return dedupeUsers(ids.stream().map(this::getUserById).collect(Collectors.toList()))
                    .stream()
                    .peek(user -> validateAssignableToTeam(team, leaderKeycloakId, user))
                    .collect(Collectors.toList());
        }

        if (req.getAssigneeId() == null) {
            throw new BadRequestException("Assignee is required.");
        }

        User assignee = getUserById(req.getAssigneeId());
        validateAssignableToTeam(team, leaderKeycloakId, assignee);
        return List.of(assignee);
    }

    private User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private List<User> dedupeUsers(List<User> users) {
        Map<Long, User> unique = new LinkedHashMap<>();
        for (User user : users) {
            if (user != null && user.getId() != null) unique.put(user.getId(), user);
        }
        return new ArrayList<>(unique.values());
    }

    private void validateAssignableToTeam(Team team, String leaderKeycloakId, User assignee) {
        boolean isMember = (assignee.getTeam() != null && assignee.getTeam().getId().equals(team.getId()))
                || (assignee.getRole() == TypeRole.TEAM_LEADER && assignee.getKeycloakId().equals(leaderKeycloakId));
        if (!isMember) {
            throw new BadRequestException("Assignee is not a member of your team.");
        }
    }

    private AssignmentPreview previewAssignments(List<User> assignees, LocalDate taskStart, LocalDate taskEnd) {
        AssignmentPreview preview = new AssignmentPreview();
        List<String> dateIssues = collectDateIssues(taskStart, taskEnd);
        int totalTaskWorkingDays = (taskStart != null && taskEnd != null)
                ? workingDayService.countWorkingDays(taskStart, taskEnd)
                : 0;

        for (User assignee : assignees) {
            Map<String, Object> summary = assigneeSummary(assignee);

            if (!dateIssues.isEmpty()) {
                preview.blocked.add(withReasons(summary, dateIssues));
                continue;
            }

            if (taskStart == null || taskEnd == null) {
                preview.eligible.add(summary);
                preview.creatableAssignees.add(assignee);
                continue;
            }

            List<LeaveRequest> overlaps = leaveRequestRepository.findByUserIdAndDateRangeAndStatusIn(
                    assignee.getId(),
                    taskStart,
                    taskEnd,
                    List.of(LeaveStatus.APPROVED, LeaveStatus.PENDING)
            );

            Set<LocalDate> approvedLeaveWorkingDates = new LinkedHashSet<>();
            boolean hasApprovedLeaveOverlap = overlaps.stream()
                    .filter(leave -> leave.getStatus() == LeaveStatus.APPROVED)
                    .peek(leave -> approvedLeaveWorkingDates.addAll(workingDatesInOverlap(taskStart, taskEnd, leave)))
                    .findAny()
                    .isPresent();

            int availableWorkingDays = totalTaskWorkingDays - approvedLeaveWorkingDates.size();
            // Approved leave blocks assignment only when it covers every working day in the task window.
            if (hasApprovedLeaveOverlap && approvedLeaveWorkingDates.size() > 0 && availableWorkingDays <= 0) {
                preview.blocked.add(withReasons(summary, List.of("Employee has no available working days during this task period.")));
                continue;
            }

            List<String> warningReasons = overlaps.stream()
                    .filter(leave -> leave.getStatus() == LeaveStatus.PENDING)
                    .sorted(Comparator.comparing(LeaveRequest::getStartDate))
                    .map(leave -> String.format("Pending leave overlaps %s to %s", leave.getStartDate(), leave.getEndDate()))
                    .collect(Collectors.toList());

            if (hasApprovedLeaveOverlap && approvedLeaveWorkingDates.size() > 0) {
                warningReasons.add(0, String.format(
                        "Approved leave overlaps part of this task period. Employee still has %d available working day(s).",
                        availableWorkingDays
                ));
            }

            List<AuthorizationRequest> shortAbsences = authorizationRequestRepository
                    .findByUserAndAuthorizationTypeAndStatusAndAbsenceDateBetweenOrderByAbsenceDateAscFromTimeAsc(
                            assignee,
                            AuthorizationType.TIME_PERMISSION,
                            RequestStatus.APPROVED,
                            taskStart,
                            taskEnd
                    );
            warningReasons.addAll(shortAbsences.stream()
                    .map(this::shortAbsenceWarning)
                    .collect(Collectors.toList()));

            // Partial approved leave, pending leave, and approved short absences warn while keeping assignment creatable.
            if (!warningReasons.isEmpty()) {
                preview.warned.add(withReasons(summary, warningReasons));
            } else {
                preview.eligible.add(summary);
            }
            preview.creatableAssignees.add(assignee);
        }

        preview.requestedCount = assignees.size();
        preview.eligibleCount = preview.eligible.size();
        preview.warnedCount = preview.warned.size();
        preview.blockedCount = preview.blocked.size();
        preview.canCreate = !preview.creatableAssignees.isEmpty();
        return preview;
    }

    private Set<LocalDate> workingDatesInOverlap(LocalDate taskStart, LocalDate taskEnd, LeaveRequest leave) {
        Set<LocalDate> dates = new LinkedHashSet<>();
        if (taskStart == null || taskEnd == null || leave.getStartDate() == null || leave.getEndDate() == null) {
            return dates;
        }

        LocalDate overlapStart = taskStart.isAfter(leave.getStartDate()) ? taskStart : leave.getStartDate();
        LocalDate overlapEnd = taskEnd.isBefore(leave.getEndDate()) ? taskEnd : leave.getEndDate();
        LocalDate current = overlapStart;
        while (!current.isAfter(overlapEnd)) {
            if (workingDayService.isWorkingDay(current)) {
                dates.add(current);
            }
            current = current.plusDays(1);
        }
        return dates;
    }

    private String shortAbsenceWarning(AuthorizationRequest request) {
        return String.format(
                "Approved short absence on %s from %s to %s.",
                request.getAbsenceDate(),
                request.getFromTime() != null ? request.getFromTime().format(TIME_FORMATTER) : "time not specified",
                request.getToTime() != null ? request.getToTime().format(TIME_FORMATTER) : "time not specified"
        );
    }

    private List<String> collectDateIssues(LocalDate startDate, LocalDate endDate) {
        Set<String> issues = new LinkedHashSet<>();
        issues.addAll(collectPastDateIssues(startDate, endDate));
        if (startDate != null && !workingDayService.isWorkingDay(startDate)) {
            issues.add(dateIssueLabel("Start date", startDate));
        }
        if (endDate != null && !endDate.equals(startDate) && !workingDayService.isWorkingDay(endDate)) {
            issues.add(dateIssueLabel("Due date", endDate));
        }
        return new ArrayList<>(issues);
    }

    private List<String> collectPastDateIssues(LocalDate startDate, LocalDate endDate) {
        List<String> issues = new ArrayList<>();
        LocalDate today = LocalDate.now();
        if (startDate != null && startDate.isBefore(today)) {
            issues.add("Start date cannot be in the past.");
        }
        if (endDate != null && endDate.isBefore(today)) {
            issues.add("Due date cannot be in the past.");
        }
        return issues;
    }

    private String dateIssueLabel(String label, LocalDate date) {
        if (workingDayService.isPublicHoliday(date)) {
            return label + " falls on a public holiday.";
        }
        return label + " falls on a weekend.";
    }

    private String firstBlockingReason(AssignmentPreview preview) {
        if (preview.blocked.isEmpty()) {
            return "Task cannot be updated because assignment availability is blocked.";
        }
        Object reasons = preview.blocked.get(0).get("reasons");
        if (reasons instanceof List<?> list && !list.isEmpty()) {
            return String.valueOf(list.get(0));
        }
        return "Task cannot be updated because assignment availability is blocked.";
    }

    private Map<String, Object> assigneeSummary(User assignee) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("assigneeId", assignee.getId());
        data.put("assigneeName", assignee.getPerson() != null
                ? (assignee.getPerson().getFirstName() + " " + assignee.getPerson().getLastName()).trim()
                : assignee.getUsername());
        data.put("assigneeUsername", assignee.getUsername());
        return data;
    }

    private Map<String, Object> withReasons(Map<String, Object> data, List<String> reasons) {
        Map<String, Object> copy = new LinkedHashMap<>(data);
        copy.put("reasons", reasons);
        return copy;
    }

    static class TaskAssignmentContext {
        final Team team;
        final Project project;
        final List<User> requestedAssignees;
        final AssignmentPreview preview;

        private TaskAssignmentContext(Team team, Project project, List<User> requestedAssignees, AssignmentPreview preview) {
            this.team = team;
            this.project = project;
            this.requestedAssignees = requestedAssignees;
            this.preview = preview;
        }
    }

    static class AssignmentPreview {
        final List<Map<String, Object>> eligible = new ArrayList<>();
        final List<Map<String, Object>> warned = new ArrayList<>();
        final List<Map<String, Object>> blocked = new ArrayList<>();
        final List<User> creatableAssignees = new ArrayList<>();
        boolean canCreate;
        int requestedCount;
        int eligibleCount;
        int warnedCount;
        int blockedCount;

        Map<String, Object> toMap() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("canCreate", canCreate);
            data.put("requestedCount", requestedCount);
            data.put("eligibleCount", eligibleCount);
            data.put("warnedCount", warnedCount);
            data.put("blockedCount", blockedCount);
            data.put("eligibleAssignees", eligible);
            data.put("warnedAssignees", warned);
            data.put("blockedAssignees", blocked);
            return data;
        }
    }
}
