package tn.isetbizerte.pfe.hrbackend.modules.assistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import tn.isetbizerte.pfe.hrbackend.common.enums.LeaveType;
import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;
import tn.isetbizerte.pfe.hrbackend.common.exception.ResourceNotFoundException;
import tn.isetbizerte.pfe.hrbackend.modules.employee.dto.LeaveBalanceDto;
import tn.isetbizerte.pfe.hrbackend.modules.employee.service.LeaveBalanceService;
import tn.isetbizerte.pfe.hrbackend.modules.hr.dto.RequestActionSummary;
import tn.isetbizerte.pfe.hrbackend.modules.hr.service.DashboardRequestSummaryService;
import tn.isetbizerte.pfe.hrbackend.modules.team.service.TeamService;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.Person;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.service.AuthenticatedUserResolver;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AssistantContextBuilder
 * -----------------------
 * Builds a safe, role-scoped context object to be forwarded to the FastAPI AI service.
 *
 * STRICT DATA RULES — the following are NEVER included in the output:
 *   - JWT access token or refresh token
 *   - keycloakId (internal Keycloak UUID)
 *   - Keycloak realm name, client ID, or secrets
 *   - Password fields of any kind
 *   - Salary or monthly deduction amounts
 *   - Birth date, home address, phone number
 *   - Avatar photo (can be megabytes of base64)
 *   - Full employee list or raw JPA entity data
 *
 * What IS included depends on role:
 *   All roles         : displayName (firstName + lastName if available)
 *   EMPLOYEE          : annual leave available days, open request counts
 *   TEAM_LEADER       : same as EMPLOYEE + team name and member count (graceful if no team)
 *   HR_MANAGER        : total pending platform actions count only
 */
@Service
public class AssistantContextBuilder {

    private static final Logger logger = LoggerFactory.getLogger(AssistantContextBuilder.class);

    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final LeaveBalanceService leaveBalanceService;
    private final DashboardRequestSummaryService dashboardRequestSummaryService;
    private final TeamService teamService;

    public AssistantContextBuilder(
            AuthenticatedUserResolver authenticatedUserResolver,
            LeaveBalanceService leaveBalanceService,
            DashboardRequestSummaryService dashboardRequestSummaryService,
            TeamService teamService
    ) {
        this.authenticatedUserResolver = authenticatedUserResolver;
        this.leaveBalanceService = leaveBalanceService;
        this.dashboardRequestSummaryService = dashboardRequestSummaryService;
        this.teamService = teamService;
    }

    /**
     * Build and return the safe context map for the given JWT.
     * Never throws on optional context failures — partial context is always safe to send.
     */
    public Map<String, Object> build(Jwt jwt) {
        User user = authenticatedUserResolver.require(jwt);
        TypeRole role = user.getRole();
        String keycloakId = jwt.getSubject();

        Map<String, Object> context = new HashMap<>();

        // --- Display name (always included when Person data is available) ---
        addDisplayName(context, user.getPerson());

        // --- Role-specific context ---
        switch (role) {
            case EMPLOYEE -> {
                addLeaveContext(context, keycloakId);
                addRequestContext(context, keycloakId);
            }
            case TEAM_LEADER -> {
                addLeaveContext(context, keycloakId);
                addRequestContext(context, keycloakId);
                addTeamContext(context, keycloakId);
            }
            case HR_MANAGER -> {
                addHrContext(context);
            }
            default -> {
                // NEW_USER: no context — should not reach here (blocked at SecurityConfig)
            }
        }

        return context;
    }

    // ---------------------------------------------------------------------------
    // Private builders
    // ---------------------------------------------------------------------------

    private void addDisplayName(Map<String, Object> context, Person person) {
        if (person == null) return;
        String firstName = person.getFirstName();
        String lastName  = person.getLastName();
        if (firstName != null && lastName != null) {
            context.put("displayName", firstName + " " + lastName);
        } else if (firstName != null) {
            context.put("displayName", firstName);
        }
    }

    private void addLeaveContext(Map<String, Object> context, String keycloakId) {
        try {
            int year = LocalDate.now().getYear();
            List<LeaveBalanceDto> balances = leaveBalanceService.getMyBalances(keycloakId, year);

            BigDecimal annualAvailable = balances.stream()
                    .filter(b -> LeaveType.ANNUAL.equals(b.getLeaveType()))
                    .findFirst()
                    .map(LeaveBalanceDto::getAvailableDays)
                    .orElse(null);

            if (annualAvailable != null) {
                Map<String, Object> leaveCtx = new HashMap<>();
                leaveCtx.put("annualAvailableDays", annualAvailable.intValue());
                context.put("leave", leaveCtx);
            }
        } catch (Exception e) {
            // Leave context is optional — log and continue
            logger.warn("Could not load leave context for assistant: {}", e.getMessage());
        }
    }

    private void addRequestContext(Map<String, Object> context, String keycloakId) {
        try {
            RequestActionSummary summary =
                    dashboardRequestSummaryService.getEmployeeOpenRequestsSummary(keycloakId);

            Map<String, Object> requestsCtx = new HashMap<>();
            requestsCtx.put("totalPending",  summary.total());
            requestsCtx.put("leavesPending", summary.leavesPending());
            context.put("requests", requestsCtx);
        } catch (Exception e) {
            logger.warn("Could not load request context for assistant: {}", e.getMessage());
        }
    }

    private void addTeamContext(Map<String, Object> context, String keycloakId) {
        try {
            Map<String, Object> team = teamService.getMyTeam(keycloakId);

            Map<String, Object> teamCtx = new HashMap<>();
            teamCtx.put("teamName",    team.get("name"));
            teamCtx.put("memberCount", team.get("memberCount"));
            context.put("team", teamCtx);
        } catch (ResourceNotFoundException e) {
            // Team Leader may not yet have an assigned team — this is valid
            logger.debug("Team Leader has no team assigned; omitting team context from assistant.");
        } catch (Exception e) {
            logger.warn("Could not load team context for assistant: {}", e.getMessage());
        }
    }

    private void addHrContext(Map<String, Object> context) {
        try {
            RequestActionSummary summary =
                    dashboardRequestSummaryService.getHrRequestActionSummary();

            Map<String, Object> hrCtx = new HashMap<>();
            hrCtx.put("totalPendingActions", summary.total());
            context.put("hr", hrCtx);
        } catch (Exception e) {
            logger.warn("Could not load HR context for assistant: {}", e.getMessage());
        }
    }
}
