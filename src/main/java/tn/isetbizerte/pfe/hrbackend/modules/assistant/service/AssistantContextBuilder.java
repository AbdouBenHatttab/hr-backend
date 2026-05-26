package tn.isetbizerte.pfe.hrbackend.modules.assistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import tn.isetbizerte.pfe.hrbackend.common.enums.ApprovalDecision;
import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveType;
import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveStatus;
import tn.isetbizerte.pfe.hrbackend.common.enums.TaskPriority;
import tn.isetbizerte.pfe.hrbackend.common.enums.TaskStatus;
import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;
import tn.isetbizerte.pfe.hrbackend.common.exception.ResourceNotFoundException;
import tn.isetbizerte.pfe.hrbackend.modules.assistant.dto.SafeAssistantContext;
import tn.isetbizerte.pfe.hrbackend.modules.employee.dto.LeaveBalanceDto;
import tn.isetbizerte.pfe.hrbackend.modules.employee.entity.LeaveRequest;
import tn.isetbizerte.pfe.hrbackend.modules.employee.repository.LeaveRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.employee.service.LeaveBalanceService;
import tn.isetbizerte.pfe.hrbackend.modules.hr.dto.RequestActionSummary;
import tn.isetbizerte.pfe.hrbackend.modules.hr.service.DashboardRequestSummaryService;
import tn.isetbizerte.pfe.hrbackend.modules.task.entity.Task;
import tn.isetbizerte.pfe.hrbackend.modules.task.repository.TaskRepository;
import tn.isetbizerte.pfe.hrbackend.modules.team.entity.Team;
import tn.isetbizerte.pfe.hrbackend.modules.team.repository.TeamRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.Person;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.service.AuthenticatedUserResolver;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AssistantContextBuilder
 * -----------------------
 * Builds a typed, safe, role-scoped {@link SafeAssistantContext} to be forwarded
 * to the FastAPI AI service.
 *
 * STRICT DATA RULES — the following are NEVER included in the output:
 *   - JWT access token or refresh token
 *   - keycloakId (internal Keycloak UUID)
 *   - Keycloak realm name, client ID, or secrets
 *   - Password fields of any kind
 *   - Salary or monthly deduction amounts
 *   - Birth date, home address, phone number, email
 *   - Avatar photo (can be megabytes of base64)
 *   - Full employee list, team member lists, or raw JPA entity data
 *   - maritalStatus, numberOfChildren, login history, document file paths
 *
 * What IS included depends on role:
 *   All roles     : displayName (firstName + lastName only — no email, no ID)
 *   EMPLOYEE      : annual + sick leave balances, full open request breakdown
 *   TEAM_LEADER   : same as EMPLOYEE + safe team summary + pending TL approvals count
 *   HR_MANAGER    : platform-wide pending action counts + new-user onboarding count
 *   NEW_USER      : empty context (safety backstop — endpoint blocks this role)
 *
 * All optional context failures are caught and logged; a partial context is always
 * safer to forward than throwing an error and blocking the user.
 */
@Service
public class AssistantContextBuilder {

    private static final Logger logger = LoggerFactory.getLogger(AssistantContextBuilder.class);

    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final LeaveBalanceService leaveBalanceService;
    private final DashboardRequestSummaryService dashboardRequestSummaryService;
    private final TeamRepository teamRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    public AssistantContextBuilder(
            AuthenticatedUserResolver authenticatedUserResolver,
            LeaveBalanceService leaveBalanceService,
            DashboardRequestSummaryService dashboardRequestSummaryService,
            TeamRepository teamRepository,
            LeaveRequestRepository leaveRequestRepository,
            TaskRepository taskRepository,
            UserRepository userRepository
    ) {
        this.authenticatedUserResolver = authenticatedUserResolver;
        this.leaveBalanceService = leaveBalanceService;
        this.dashboardRequestSummaryService = dashboardRequestSummaryService;
        this.teamRepository = teamRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
    }

    /**
     * Build and return the typed safe context for the given JWT.
     * Never throws on optional context failures — partial context is always safe to forward.
     */
    public SafeAssistantContext build(Jwt jwt) {
        return build(jwt, null);
    }

    /**
     * Build and return the typed safe context for the given JWT plus optional
     * selected Team Leader leave request context. Never throws on optional
     * context failures — partial context is always safe to forward.
     */
    public SafeAssistantContext build(Jwt jwt, Long selectedLeaveRequestId) {
        User user = authenticatedUserResolver.require(jwt);
        TypeRole role = user.getRole();
        String keycloakId = user.getKeycloakId();

        String displayName = resolveDisplayName(user.getPerson());

        return switch (role) {
            case EMPLOYEE -> new SafeAssistantContext(
                    displayName,
                    buildEmployeeContext(keycloakId),
                    null,
                    null,
                    null
            );
case TEAM_LEADER -> new SafeAssistantContext(
                displayName,
                buildEmployeeContext(keycloakId),
                buildTeamContext(user),
                null,
                buildTeamLeaveDecisionContext(user, selectedLeaveRequestId)
            );
            case HR_MANAGER -> new SafeAssistantContext(
                    displayName,
                    null,
                    null,
                    buildHrContext(),
                    null
            );
            default -> // NEW_USER: endpoint blocks this role; empty context as safety backstop
                    new SafeAssistantContext(null, null, null, null, null);
        };
    }

    // ---------------------------------------------------------------------------
    // Display name
    // ---------------------------------------------------------------------------

    /**
     * Returns "First Last" when both parts are present, "First" when only the first
     * name is available, or null when no Person exists or both name parts are missing.
     * Never returns email, username, or any identifier.
     */
    private String resolveDisplayName(Person person) {
        if (person == null) return null;
        String first = person.getFirstName();
        String last  = person.getLastName();
        if (first != null && !first.isBlank() && last != null && !last.isBlank()) {
            return first.trim() + " " + last.trim();
        }
        if (first != null && !first.isBlank()) return first.trim();
        return null;
    }

    // ---------------------------------------------------------------------------
    // EMPLOYEE context
    // ---------------------------------------------------------------------------

    /**
     * Builds the personal employee context. Failures on individual sub-sections
     * are caught independently so a leave-service outage does not wipe request counts.
     */
    private SafeAssistantContext.EmployeeContext buildEmployeeContext(String keycloakId) {
        Integer annualDays = null;
        Integer sickDays   = null;

        try {
            int year = LocalDate.now().getYear();
            List<LeaveBalanceDto> balances = leaveBalanceService.getMyBalances(keycloakId, year);

            annualDays = balances.stream()
                    .filter(b -> LeaveType.ANNUAL.equals(b.getLeaveType()))
                    .findFirst()
                    .map(LeaveBalanceDto::getAvailableDays)
                    .filter(v -> v != null)
                    .map(BigDecimal::intValue)
                    .orElse(null);

            sickDays = balances.stream()
                    .filter(b -> LeaveType.SICK.equals(b.getLeaveType()))
                    .findFirst()
                    .map(LeaveBalanceDto::getAvailableDays)
                    .filter(v -> v != null)
                    .map(BigDecimal::intValue)
                    .orElse(null);

        } catch (Exception e) {
            logger.warn("Could not load leave balances for assistant employee context: {}", e.getMessage());
        }

        long total = 0, leavesPending = 0, documentsPending = 0, documentsAwaitingFile = 0,
                loansPending = 0, authorizationsPending = 0;
        try {
            RequestActionSummary summary =
                    dashboardRequestSummaryService.getEmployeeOpenRequestsSummary(keycloakId);
            total                 = summary.total();
            leavesPending         = summary.leavesPending();
            documentsPending      = summary.documentsPending();
            // Exposed separately — AI assistant can distinguish "submitted" vs "approved, awaiting upload"
            documentsAwaitingFile = summary.documentsAwaitingFile();
            // loansAwaitingFile merged into loansPending: both states are indistinguishable to the employee
            loansPending          = summary.loansPending() + summary.loansAwaitingFile();
            authorizationsPending = summary.authorizationsPending();
        } catch (Exception e) {
            logger.warn("Could not load request summary for assistant employee context: {}", e.getMessage());
        }

        return new SafeAssistantContext.EmployeeContext(
                annualDays,
                sickDays,
                total,
                leavesPending,
                documentsPending,
                documentsAwaitingFile,
                loansPending,
                authorizationsPending
        );
    }

    // ---------------------------------------------------------------------------
    // TEAM_LEADER team context
    // ---------------------------------------------------------------------------

    /**
     * Builds a safe team summary. Returns a TeamContext with null sub-fields when
     * no team is assigned yet (valid state for a newly promoted Team Leader).
     *
     * NEVER reads TeamService.getMyTeam() — that map carries member personalInfo.
     * Reads directly from TeamRepository (name only) and two safe count queries.
     */
    private SafeAssistantContext.TeamContext buildTeamContext(String keycloakId) {
        try {
            var teamOpt = teamRepository.findByTeamLeaderKeycloakId(keycloakId);
            if (teamOpt.isEmpty()) {
                logger.debug("Team Leader {} has no team assigned; omitting team sub-fields.", keycloakId);
                return new SafeAssistantContext.TeamContext(null, null, 0L);
            }

            Team team = teamOpt.get();
            Long teamId   = team.getId();
            String teamName = team.getName();

            // Safe scalar count — no entity data
            int memberCount = (int) userRepository.countByTeamId(teamId);

            // Safe scalar count — added in this step to LeaveRequestRepository
            long pendingApprovals = leaveRequestRepository
                    .countPendingTeamLeaderApprovalsByTeamId(teamId, keycloakId);

            return new SafeAssistantContext.TeamContext(teamName, memberCount, pendingApprovals);

        } catch (ResourceNotFoundException e) {
            logger.debug("Team Leader has no team assigned; returning empty team context.");
            return new SafeAssistantContext.TeamContext(null, null, 0L);
        } catch (Exception e) {
            logger.warn("Could not load team context for assistant: {}", e.getMessage());
            return new SafeAssistantContext.TeamContext(null, null, 0L);
        }
    }

    private SafeAssistantContext.TeamContext buildTeamContext(User leader) {
        if (leader == null || leader.getId() == null) {
            return new SafeAssistantContext.TeamContext(null, null, 0L);
        }
        try {
            var teamOpt = teamRepository.findByTeamLeaderId(leader.getId());
            if (teamOpt.isEmpty()) {
                logger.debug("Team Leader {} has no team assigned; omitting team sub-fields.", leader.getId());
                return new SafeAssistantContext.TeamContext(null, null, 0L);
            }

            Team team = teamOpt.get();
            Long teamId = team.getId();
            String teamName = team.getName();

            int memberCount = (int) userRepository.countByTeamId(teamId);

            long pendingApprovals = leaveRequestRepository
                    .countPendingTeamLeaderApprovalsByTeamId(teamId, leader.getKeycloakId());

            return new SafeAssistantContext.TeamContext(teamName, memberCount, pendingApprovals);
        } catch (Exception e) {
            logger.warn("Could not load team context for assistant: {}", e.getMessage());
            return new SafeAssistantContext.TeamContext(null, null, 0L);
        }
    }

    // ---------------------------------------------------------------------------
    // TEAM_LEADER selected leave decision-support context
    // ---------------------------------------------------------------------------

    /**
     * Builds a read-only, safe context for one selected leave request.
     * Authorization mirrors the Team Leader approval boundary: the leave must belong
     * to the leader's team, must not be the leader's own request, and must not belong
     * to another Team Leader account.
     */
    private SafeAssistantContext.TeamLeaveDecisionContext buildTeamLeaveDecisionContext(
            String leaderKeycloakId,
            Long selectedLeaveRequestId
    ) {
        if (selectedLeaveRequestId == null) {
            return null;
        }

        try {
            var teamOpt = teamRepository.findByTeamLeaderKeycloakId(leaderKeycloakId);
            if (teamOpt.isEmpty()) {
                return unavailableTeamLeaveDecision(selectedLeaveRequestId, "TEAM_NOT_ASSIGNED");
            }

            Team leaderTeam = teamOpt.get();
            Long leaderTeamId = leaderTeam.getId();

            var leaveOpt = leaveRequestRepository.findByIdWithUserAndPerson(selectedLeaveRequestId);
            if (leaveOpt.isEmpty()) {
                return unavailableTeamLeaveDecision(selectedLeaveRequestId, "LEAVE_REQUEST_NOT_FOUND");
            }

            LeaveRequest leave = leaveOpt.get();
            User employee = leave.getUser();
            if (employee == null || employee.getTeam() == null || employee.getTeam().getId() == null) {
                return unavailableTeamLeaveDecision(selectedLeaveRequestId, "LEAVE_REQUEST_NOT_IN_TEAM");
            }

            if (!employee.getTeam().getId().equals(leaderTeamId)) {
                return unavailableTeamLeaveDecision(selectedLeaveRequestId, "LEAVE_REQUEST_NOT_VISIBLE");
            }

            if (leaderKeycloakId.equals(employee.getKeycloakId()) || TypeRole.TEAM_LEADER.equals(employee.getRole())) {
                return unavailableTeamLeaveDecision(selectedLeaveRequestId, "SELF_REQUEST_NOT_AVAILABLE");
            }

            OverlapCounts overlapCounts = resolveOverlapCounts(leaderTeamId, leave);
            Integer teamMemberCount = resolveTeamMemberCount(leaderTeamId);
            CoverageCounts coverageCounts = resolveTeamCoverageCounts(leaderTeamId, leave, teamMemberCount);
            WorkloadCounts workloadCounts = resolveWorkloadCounts(employee);
            List<SafeAssistantContext.TaskEvidenceSummary> taskEvidence = resolveTaskEvidence(employee, leave);

            return new SafeAssistantContext.TeamLeaveDecisionContext(
                    true,
                    null,
                    leave.getId(),
                    resolveDisplayName(employee.getPerson()),
                    leave.getLeaveType() != null ? leave.getLeaveType().name() : null,
                    leave.getStartDate(),
                    leave.getEndDate(),
                    leave.getNumberOfDays(),
                    leave.getStatus() != null ? leave.getStatus().name() : null,
                    computeApprovalStage(leave),
                    leave.getReason(),
                    overlapCounts.approved(),
                    overlapCounts.pending(),
                    teamMemberCount,
                    coverageCounts.unavailableTeamMemberCount(),
                    coverageCounts.availableTeamMemberCount(),
                    coverageCounts.teamCoverageStatus(),
                    workloadCounts.activeTaskCount(),
                    workloadCounts.dueSoonTaskCount(),
                    workloadCounts.overdueTaskCount(),
                    workloadCounts.highPriorityTaskCount(),
                    workloadCounts.available(),
                    overlapCounts.available(),
                    taskEvidence
            );
        } catch (Exception e) {
            logger.warn("Could not load Team Leader leave decision context for assistant: {}", e.getMessage());
            return unavailableTeamLeaveDecision(selectedLeaveRequestId, "CONTEXT_UNAVAILABLE");
        }
    }

    private SafeAssistantContext.TeamLeaveDecisionContext buildTeamLeaveDecisionContext(
            User leader,
            Long selectedLeaveRequestId
    ) {
        if (selectedLeaveRequestId == null) {
            return null;
        }
        if (leader == null || leader.getId() == null) {
            return unavailableTeamLeaveDecision(selectedLeaveRequestId, "TEAM_NOT_ASSIGNED");
        }

        try {
            var teamOpt = teamRepository.findByTeamLeaderId(leader.getId());
            if (teamOpt.isEmpty()) {
                return unavailableTeamLeaveDecision(selectedLeaveRequestId, "TEAM_NOT_ASSIGNED");
            }

            Team leaderTeam = teamOpt.get();
            Long leaderTeamId = leaderTeam.getId();

            var leaveOpt = leaveRequestRepository.findByIdWithUserAndPerson(selectedLeaveRequestId);
            if (leaveOpt.isEmpty()) {
                return unavailableTeamLeaveDecision(selectedLeaveRequestId, "LEAVE_REQUEST_NOT_FOUND");
            }

            LeaveRequest leave = leaveOpt.get();
            User employee = leave.getUser();
            if (employee == null || employee.getTeam() == null || employee.getTeam().getId() == null) {
                return unavailableTeamLeaveDecision(selectedLeaveRequestId, "LEAVE_REQUEST_NOT_IN_TEAM");
            }

            if (!employee.getTeam().getId().equals(leaderTeamId)) {
                return unavailableTeamLeaveDecision(selectedLeaveRequestId, "LEAVE_REQUEST_NOT_VISIBLE");
            }

            if (leader.getId().equals(employee.getId()) || TypeRole.TEAM_LEADER.equals(employee.getRole())) {
                return unavailableTeamLeaveDecision(selectedLeaveRequestId, "SELF_REQUEST_NOT_AVAILABLE");
            }

            OverlapCounts overlapCounts = resolveOverlapCounts(leaderTeamId, leave);
            Integer teamMemberCount = resolveTeamMemberCount(leaderTeamId);
            CoverageCounts coverageCounts = resolveTeamCoverageCounts(leaderTeamId, leave, teamMemberCount);
            WorkloadCounts workloadCounts = resolveWorkloadCounts(employee);
            List<SafeAssistantContext.TaskEvidenceSummary> taskEvidence = resolveTaskEvidence(employee, leave);

            return new SafeAssistantContext.TeamLeaveDecisionContext(
                    true,
                    null,
                    leave.getId(),
                    resolveDisplayName(employee.getPerson()),
                    leave.getLeaveType() != null ? leave.getLeaveType().name() : null,
                    leave.getStartDate(),
                    leave.getEndDate(),
                    leave.getNumberOfDays(),
                    leave.getStatus() != null ? leave.getStatus().name() : null,
                    computeApprovalStage(leave),
                    leave.getReason(),
                    overlapCounts.approved(),
                    overlapCounts.pending(),
                    teamMemberCount,
                    coverageCounts.unavailableTeamMemberCount(),
                    coverageCounts.availableTeamMemberCount(),
                    coverageCounts.teamCoverageStatus(),
                    workloadCounts.activeTaskCount(),
                    workloadCounts.dueSoonTaskCount(),
                    workloadCounts.overdueTaskCount(),
                    workloadCounts.highPriorityTaskCount(),
                    workloadCounts.available(),
                    overlapCounts.available(),
                    taskEvidence
            );
        } catch (Exception e) {
            logger.warn("Could not load Team Leader leave decision context for assistant: {}", e.getMessage());
            return unavailableTeamLeaveDecision(selectedLeaveRequestId, "CONTEXT_UNAVAILABLE");
        }
    }

    private SafeAssistantContext.TeamLeaveDecisionContext unavailableTeamLeaveDecision(
            Long selectedLeaveRequestId,
            String reason
    ) {
            return new SafeAssistantContext.TeamLeaveDecisionContext(
                false,
                reason,
                selectedLeaveRequestId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0L,
                0L,
                null,
                null,
                null,
                "UNKNOWN",
                0L,
                0L,
                0L,
                0L,
                false,
                false,
                List.of()
        );
    }

    private WorkloadCounts resolveWorkloadCounts(User employee) {
        if (employee == null || employee.getId() == null) {
            return new WorkloadCounts(0L, 0L, 0L, 0L, false);
        }

        try {
            List<Task> tasks = taskRepository.findByAssigneeId(employee.getId());
            if (tasks == null) {
                return new WorkloadCounts(0L, 0L, 0L, 0L, false);
            }

            LocalDate today = LocalDate.now();
            LocalDate dueSoonUntil = today.plusDays(7);

            long active = tasks.stream()
                    .filter(this::isOpenTask)
                    .count();
            long dueSoon = tasks.stream()
                    .filter(this::isOpenTask)
                    .filter(task -> task.getDueDate() != null
                            && !task.getDueDate().isBefore(today)
                            && !task.getDueDate().isAfter(dueSoonUntil))
                    .count();
            long overdue = tasks.stream()
                    .filter(this::isOpenTask)
                    .filter(task -> task.getDueDate() != null
                            && task.getDueDate().isBefore(today))
                    .count();
            long highPriority = tasks.stream()
                    .filter(this::isOpenTask)
                    .filter(task -> task.getPriority() == TaskPriority.HIGH)
                    .count();

            return new WorkloadCounts(active, dueSoon, overdue, highPriority, true);
        } catch (Exception e) {
            logger.warn("Could not load workload context for assistant leave decision: {}", e.getMessage());
            return new WorkloadCounts(0L, 0L, 0L, 0L, false);
        }
    }

    private List<SafeAssistantContext.TaskEvidenceSummary> resolveTaskEvidence(User employee, LeaveRequest selectedLeave) {
        if (employee == null || employee.getId() == null || selectedLeave == null) {
            return List.of();
        }

        try {
            LocalDate today = LocalDate.now();
            LocalDate dueSoonUntil = today.plusDays(7);
            LocalDate leaveStart = selectedLeave.getStartDate();
            LocalDate leaveEnd = selectedLeave.getEndDate();

            List<Task> tasks = taskRepository.findByAssigneeId(employee.getId());
            if (tasks == null || tasks.isEmpty()) {
                return List.of();
            }

            return tasks.stream()
                    .filter(this::isOpenTask)
                    .map(task -> new TaskEvidenceCandidate(task, taskImpact(task, today, dueSoonUntil, leaveStart, leaveEnd)))
                    .filter(candidate -> candidate.impact != null)
                    .sorted(Comparator
                            .comparingInt((TaskEvidenceCandidate candidate) -> impactRank(candidate.impact))
                            .thenComparing(candidate -> candidate.task.getDueDate(), Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(candidate -> String.valueOf(candidate.task.getTitle()).toLowerCase())
                    )
                    .limit(5)
                    .map(candidate -> mapTaskEvidence(candidate.task, candidate.impact))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.warn("Could not load task evidence for assistant leave decision context: {}", e.getMessage());
            return List.of();
        }
    }

    private SafeAssistantContext.TaskEvidenceSummary mapTaskEvidence(Task task, String impact) {
        return new SafeAssistantContext.TaskEvidenceSummary(
                task.getTitle(),
                task.getProject() != null ? task.getProject().getName() : null,
                task.getStatus() != null ? task.getStatus().name() : null,
                task.getPriority() != null ? task.getPriority().name() : null,
                task.getDueDate(),
                impact
        );
    }

    private String taskImpact(Task task,
                              LocalDate today,
                              LocalDate dueSoonUntil,
                              LocalDate leaveStart,
                              LocalDate leaveEnd) {
        LocalDate dueDate = task.getDueDate();
        if (dueDate == null) {
            return "ACTIVE_ASSIGNED_TASK";
        }
        if (dueDate.isBefore(today)) {
            return "OVERDUE";
        }

        boolean dueDuringLeave = leaveStart != null && leaveEnd != null
                && !dueDate.isBefore(leaveStart)
                && !dueDate.isAfter(leaveEnd);

        if (dueDuringLeave && task.getPriority() == TaskPriority.HIGH) {
            return "HIGH_PRIORITY_DUE_DURING_LEAVE";
        }
        if (dueDuringLeave) {
            return "DUE_DURING_LEAVE";
        }
        if (!dueDate.isBefore(today) && !dueDate.isAfter(dueSoonUntil)) {
            return "DUE_SOON";
        }
        return "ACTIVE";
    }

    private int impactRank(String impact) {
        if ("OVERDUE".equals(impact)) return 0;
        if ("HIGH_PRIORITY_DUE_DURING_LEAVE".equals(impact)) return 1;
        if ("DUE_DURING_LEAVE".equals(impact)) return 2;
        if ("DUE_SOON".equals(impact)) return 3;
        if ("ACTIVE_ASSIGNED_TASK".equals(impact)) return 4;
        return 5;
    }

    private record TaskEvidenceCandidate(Task task, String impact) {}

    private boolean isOpenTask(Task task) {
        return task != null && task.getStatus() != TaskStatus.DONE;
    }

    private OverlapCounts resolveOverlapCounts(Long teamId, LeaveRequest selectedLeave) {
        try {
            List<LeaveRequest> overlaps = leaveRequestRepository.findByTeamIdAndDateRangeAndStatusIn(
                    teamId,
                    selectedLeave.getStartDate(),
                    selectedLeave.getEndDate(),
                    List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED)
            );

            long approved = overlaps.stream()
                    .filter(lr -> !selectedLeave.getId().equals(lr.getId()))
                    .filter(lr -> LeaveStatus.APPROVED.equals(lr.getStatus()))
                    .count();

            long pending = overlaps.stream()
                    .filter(lr -> !selectedLeave.getId().equals(lr.getId()))
                    .filter(lr -> LeaveStatus.PENDING.equals(lr.getStatus()))
                    .count();

            return new OverlapCounts(approved, pending, true);
        } catch (Exception e) {
            logger.warn("Could not load overlap counts for assistant leave decision context: {}", e.getMessage());
            return new OverlapCounts(0L, 0L, false);
        }
    }

    private Integer resolveTeamMemberCount(Long teamId) {
        try {
            return (int) userRepository.countByTeamId(teamId);
        } catch (Exception e) {
            logger.warn("Could not load team member count for assistant leave decision context: {}", e.getMessage());
            return null;
        }
    }

    private CoverageCounts resolveTeamCoverageCounts(Long teamId, LeaveRequest selectedLeave, Integer teamMemberCount) {
        try {
            List<LeaveRequest> overlaps = leaveRequestRepository.findByTeamIdAndDateRangeAndStatusIn(
                    teamId,
                    selectedLeave.getStartDate(),
                    selectedLeave.getEndDate(),
                    List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED)
            );

            Long selectedEmployeeId = selectedLeave.getUser() != null ? selectedLeave.getUser().getId() : null;
            Set<Long> unavailableMemberIds = overlaps.stream()
                    .filter(lr -> lr.getUser() != null)
                    .map(LeaveRequest::getUser)
                    .map(User::getId)
                    .filter(Objects::nonNull)
                    .filter(id -> selectedEmployeeId == null || !selectedEmployeeId.equals(id))
                    .collect(Collectors.toSet());

            Integer unavailableTeamMemberCount = unavailableMemberIds.size();
            if (teamMemberCount == null) {
                return new CoverageCounts(unavailableTeamMemberCount, null, "UNKNOWN");
            }

            int availableTeamMemberCount = Math.max(teamMemberCount - unavailableTeamMemberCount, 0);
            String teamCoverageStatus = computeTeamCoverageStatus(teamMemberCount, availableTeamMemberCount, unavailableTeamMemberCount);
            return new CoverageCounts(unavailableTeamMemberCount, availableTeamMemberCount, teamCoverageStatus);
        } catch (Exception e) {
            logger.warn("Could not load team coverage for assistant leave decision context: {}", e.getMessage());
            return new CoverageCounts(null, null, "UNKNOWN");
        }
    }

    private String computeTeamCoverageStatus(int teamMemberCount, int availableTeamMemberCount, int unavailableTeamMemberCount) {
        if (availableTeamMemberCount <= 1) {
            return "CRITICAL";
        }
        if (unavailableTeamMemberCount > 0 || availableTeamMemberCount * 2 < teamMemberCount) {
            return "TIGHT";
        }
        return "NORMAL";
    }

    private String computeApprovalStage(LeaveRequest leave) {
        if (leave.getStatus() == LeaveStatus.CANCELLED_BY_EMPLOYEE) return "CANCELLED_BY_EMPLOYEE";
        if (leave.getStatus() == LeaveStatus.REJECTED) return "REJECTED";
        if (leave.getStatus() == LeaveStatus.APPROVED) return "APPROVED";
        if (leave.getTeamLeaderDecision() == ApprovalDecision.APPROVED
                && leave.getHrDecision() == ApprovalDecision.PENDING) {
            return "PENDING_HR";
        }
        if (leave.getTeamLeaderDecision() == ApprovalDecision.PENDING) {
            return "PENDING_TL";
        }
        return "PENDING";
    }

    private record OverlapCounts(long approved, long pending, boolean available) {}
    private record CoverageCounts(Integer unavailableTeamMemberCount,
                                  Integer availableTeamMemberCount,
                                  String teamCoverageStatus) {}
    private record WorkloadCounts(long activeTaskCount,
                                  long dueSoonTaskCount,
                                  long overdueTaskCount,
                                  long highPriorityTaskCount,
                                  boolean available) {}

    // ---------------------------------------------------------------------------
    // HR_MANAGER context
    // ---------------------------------------------------------------------------

    /**
     * Builds the HR management context using only aggregate counts.
     * newUsersPendingApproval uses UserRepository.countByRole() — a safe scalar.
     */
    private SafeAssistantContext.HrContext buildHrContext() {
        long totalPendingActions = 0, leavesPending = 0, documentsPending = 0, documentsAwaitingFile = 0,
                loansPending = 0, authorizationsPending = 0;
        try {
            RequestActionSummary summary =
                    dashboardRequestSummaryService.getHrRequestActionSummary();
            totalPendingActions   = summary.total();
            leavesPending         = summary.leavesPending();
            documentsPending      = summary.documentsPending();
            // Exposed separately — AI assistant can distinguish "submitted" vs "approved, awaiting upload"
            documentsAwaitingFile = summary.documentsAwaitingFile();
            // loansAwaitingFile merged into loansPending: both states are indistinguishable to HR
            loansPending          = summary.loansPending() + summary.loansAwaitingFile();
            authorizationsPending = summary.authorizationsPending();
        } catch (Exception e) {
            logger.warn("Could not load HR request action summary for assistant: {}", e.getMessage());
        }

        long newUsersPending = 0;
        try {
            newUsersPending = userRepository.countByRole(TypeRole.NEW_USER);
        } catch (Exception e) {
            logger.warn("Could not load new-users-pending count for assistant: {}", e.getMessage());
        }

        return new SafeAssistantContext.HrContext(
                totalPendingActions,
                leavesPending,
                documentsPending,
                documentsAwaitingFile,
                loansPending,
                authorizationsPending,
                newUsersPending
        );
    }
}
