package tn.isetbizerte.pfe.hrbackend.modules.assistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveType;
import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;
import tn.isetbizerte.pfe.hrbackend.common.exception.ResourceNotFoundException;
import tn.isetbizerte.pfe.hrbackend.modules.assistant.dto.SafeAssistantContext;
import tn.isetbizerte.pfe.hrbackend.modules.employee.dto.LeaveBalanceDto;
import tn.isetbizerte.pfe.hrbackend.modules.employee.repository.LeaveRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.employee.service.LeaveBalanceService;
import tn.isetbizerte.pfe.hrbackend.modules.hr.dto.RequestActionSummary;
import tn.isetbizerte.pfe.hrbackend.modules.hr.service.DashboardRequestSummaryService;
import tn.isetbizerte.pfe.hrbackend.modules.team.entity.Team;
import tn.isetbizerte.pfe.hrbackend.modules.team.repository.TeamRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.Person;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.service.AuthenticatedUserResolver;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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
    private final UserRepository userRepository;

    public AssistantContextBuilder(
            AuthenticatedUserResolver authenticatedUserResolver,
            LeaveBalanceService leaveBalanceService,
            DashboardRequestSummaryService dashboardRequestSummaryService,
            TeamRepository teamRepository,
            LeaveRequestRepository leaveRequestRepository,
            UserRepository userRepository
    ) {
        this.authenticatedUserResolver = authenticatedUserResolver;
        this.leaveBalanceService = leaveBalanceService;
        this.dashboardRequestSummaryService = dashboardRequestSummaryService;
        this.teamRepository = teamRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.userRepository = userRepository;
    }

    /**
     * Build and return the typed safe context for the given JWT.
     * Never throws on optional context failures — partial context is always safe to forward.
     */
    public SafeAssistantContext build(Jwt jwt) {
        User user = authenticatedUserResolver.require(jwt);
        TypeRole role = user.getRole();
        String keycloakId = jwt.getSubject();

        String displayName = resolveDisplayName(user.getPerson());

        return switch (role) {
            case EMPLOYEE -> new SafeAssistantContext(
                    displayName,
                    buildEmployeeContext(keycloakId),
                    null,
                    null
            );
            case TEAM_LEADER -> new SafeAssistantContext(
                    displayName,
                    buildEmployeeContext(keycloakId),
                    buildTeamContext(keycloakId),
                    null
            );
            case HR_MANAGER -> new SafeAssistantContext(
                    displayName,
                    null,
                    null,
                    buildHrContext()
            );
            default -> // NEW_USER: endpoint blocks this role; empty context as safety backstop
                    new SafeAssistantContext(null, null, null, null);
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
