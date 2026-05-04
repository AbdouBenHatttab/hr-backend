package tn.isetbizerte.pfe.hrbackend.modules.assistant.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
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
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AssistantContextBuilder.
 *
 * All dependencies are Mockito mocks — no Spring context needed.
 * Tests verify:
 *  - Each role gets the correct context keys
 *  - Forbidden data (salary, phone, address, keycloakId) is never included
 *  - TEAM_LEADER with no team assigned does not throw
 *  - HR_MANAGER does not trigger leave or team service calls
 */
class AssistantContextBuilderTest {

    // --- Mocks ---
    private AuthenticatedUserResolver authenticatedUserResolver;
    private LeaveBalanceService leaveBalanceService;
    private DashboardRequestSummaryService dashboardRequestSummaryService;
    private TeamService teamService;

    private AssistantContextBuilder contextBuilder;

    @BeforeEach
    void setUp() {
        authenticatedUserResolver      = mock(AuthenticatedUserResolver.class);
        leaveBalanceService            = mock(LeaveBalanceService.class);
        dashboardRequestSummaryService = mock(DashboardRequestSummaryService.class);
        teamService                    = mock(TeamService.class);

        contextBuilder = new AssistantContextBuilder(
                authenticatedUserResolver,
                leaveBalanceService,
                dashboardRequestSummaryService,
                teamService
        );
    }

    // ---------------------------------------------------------------------------
    // EMPLOYEE
    // ---------------------------------------------------------------------------

    @Test
    void employee_includesDisplayName() {
        Jwt jwt = jwt("kc-emp");
        User user = userWithPerson(TypeRole.EMPLOYEE, "Ahmed", "Ben Ali");
        when(authenticatedUserResolver.require(jwt)).thenReturn(user);
        stubLeaveBalance("kc-emp", 12);
        stubEmployeeRequests("kc-emp", 3, 1);

        Map<String, Object> ctx = contextBuilder.build(jwt);

        assertThat(ctx).containsKey("displayName");
        assertThat(ctx.get("displayName")).isEqualTo("Ahmed Ben Ali");
    }

    @Test
    void employee_includesLeaveAndRequestContext() {
        Jwt jwt = jwt("kc-emp");
        User user = userWithPerson(TypeRole.EMPLOYEE, "Ahmed", "Ben Ali");
        when(authenticatedUserResolver.require(jwt)).thenReturn(user);
        stubLeaveBalance("kc-emp", 15);
        stubEmployeeRequests("kc-emp", 4, 2);

        Map<String, Object> ctx = contextBuilder.build(jwt);

        assertThat(ctx).containsKey("leave");
        @SuppressWarnings("unchecked")
        Map<String, Object> leaveCtx = (Map<String, Object>) ctx.get("leave");
        assertThat(leaveCtx.get("annualAvailableDays")).isEqualTo(15);

        assertThat(ctx).containsKey("requests");
        @SuppressWarnings("unchecked")
        Map<String, Object> requestsCtx = (Map<String, Object>) ctx.get("requests");
        assertThat(requestsCtx.get("totalPending")).isEqualTo(4L);
        assertThat(requestsCtx.get("leavesPending")).isEqualTo(2L);
    }

    @Test
    void employee_doesNotIncludeTeamOrHrContext() {
        Jwt jwt = jwt("kc-emp");
        User user = userWithPerson(TypeRole.EMPLOYEE, "Ahmed", "Ben Ali");
        when(authenticatedUserResolver.require(jwt)).thenReturn(user);
        stubLeaveBalance("kc-emp", 10);
        stubEmployeeRequests("kc-emp", 0, 0);

        Map<String, Object> ctx = contextBuilder.build(jwt);

        assertThat(ctx).doesNotContainKey("team");
        assertThat(ctx).doesNotContainKey("hr");

        verify(teamService, never()).getMyTeam(anyString());
        verify(dashboardRequestSummaryService, never()).getHrRequestActionSummary();
    }

    @Test
    void employee_doesNotIncludeForbiddenFields() {
        Jwt jwt = jwt("kc-emp");
        User user = userWithPerson(TypeRole.EMPLOYEE, "Ahmed", "Ben Ali");
        when(authenticatedUserResolver.require(jwt)).thenReturn(user);
        stubLeaveBalance("kc-emp", 10);
        stubEmployeeRequests("kc-emp", 0, 0);

        Map<String, Object> ctx = contextBuilder.build(jwt);

        assertThat(ctx).doesNotContainKey("keycloakId");
        assertThat(ctx).doesNotContainKey("salary");
        assertThat(ctx).doesNotContainKey("phone");
        assertThat(ctx).doesNotContainKey("address");
        assertThat(ctx).doesNotContainKey("birthDate");
        assertThat(ctx).doesNotContainKey("avatarPhoto");
        assertThat(ctx).doesNotContainKey("password");
    }

    // ---------------------------------------------------------------------------
    // TEAM LEADER
    // ---------------------------------------------------------------------------

    @Test
    void teamLeader_includesLeaveRequestAndTeamContext() {
        Jwt jwt = jwt("kc-lead");
        User user = userWithPerson(TypeRole.TEAM_LEADER, "Sami", "Trabelsi");
        when(authenticatedUserResolver.require(jwt)).thenReturn(user);
        stubLeaveBalance("kc-lead", 8);
        stubEmployeeRequests("kc-lead", 2, 1);
        stubTeam("kc-lead", "Backend Squad", 5);

        Map<String, Object> ctx = contextBuilder.build(jwt);

        assertThat(ctx).containsKey("leave");
        assertThat(ctx).containsKey("requests");
        assertThat(ctx).containsKey("team");

        @SuppressWarnings("unchecked")
        Map<String, Object> teamCtx = (Map<String, Object>) ctx.get("team");
        assertThat(teamCtx.get("teamName")).isEqualTo("Backend Squad");
        assertThat(teamCtx.get("memberCount")).isEqualTo(5);
    }

    @Test
    void teamLeader_withNoTeamAssigned_doesNotFail() {
        Jwt jwt = jwt("kc-lead");
        User user = userWithPerson(TypeRole.TEAM_LEADER, "Sami", "Trabelsi");
        when(authenticatedUserResolver.require(jwt)).thenReturn(user);
        stubLeaveBalance("kc-lead", 8);
        stubEmployeeRequests("kc-lead", 0, 0);

        // TeamService throws ResourceNotFoundException when no team is assigned
        when(teamService.getMyTeam("kc-lead"))
                .thenThrow(new ResourceNotFoundException("No team found for this Team Leader."));

        // Must not throw — team context is optional
        Map<String, Object> ctx = contextBuilder.build(jwt);

        assertThat(ctx).doesNotContainKey("team");
        assertThat(ctx).containsKey("leave");
        assertThat(ctx).containsKey("requests");
    }

    @Test
    void teamLeader_doesNotIncludeHrContext() {
        Jwt jwt = jwt("kc-lead");
        User user = userWithPerson(TypeRole.TEAM_LEADER, "Sami", "Trabelsi");
        when(authenticatedUserResolver.require(jwt)).thenReturn(user);
        stubLeaveBalance("kc-lead", 8);
        stubEmployeeRequests("kc-lead", 0, 0);
        stubTeam("kc-lead", "Team A", 3);

        Map<String, Object> ctx = contextBuilder.build(jwt);

        assertThat(ctx).doesNotContainKey("hr");
        verify(dashboardRequestSummaryService, never()).getHrRequestActionSummary();
    }

    // ---------------------------------------------------------------------------
    // HR_MANAGER
    // ---------------------------------------------------------------------------

    @Test
    void hrManager_includesHrPendingActionsCount() {
        Jwt jwt = jwt("kc-hr");
        User user = userWithPerson(TypeRole.HR_MANAGER, "Fatma", "Amiri");
        when(authenticatedUserResolver.require(jwt)).thenReturn(user);

        RequestActionSummary summary = RequestActionSummary.of(5, 3, 2, 4, 1, 6);
        when(dashboardRequestSummaryService.getHrRequestActionSummary()).thenReturn(summary);

        Map<String, Object> ctx = contextBuilder.build(jwt);

        assertThat(ctx).containsKey("hr");
        @SuppressWarnings("unchecked")
        Map<String, Object> hrCtx = (Map<String, Object>) ctx.get("hr");
        assertThat(hrCtx.get("totalPendingActions")).isEqualTo(summary.total());
    }

    @Test
    void hrManager_doesNotIncludeLeaveOrTeamContext() {
        Jwt jwt = jwt("kc-hr");
        User user = userWithPerson(TypeRole.HR_MANAGER, "Fatma", "Amiri");
        when(authenticatedUserResolver.require(jwt)).thenReturn(user);

        RequestActionSummary summary = RequestActionSummary.of(1, 0, 0, 0, 0, 0);
        when(dashboardRequestSummaryService.getHrRequestActionSummary()).thenReturn(summary);

        Map<String, Object> ctx = contextBuilder.build(jwt);

        assertThat(ctx).doesNotContainKey("leave");
        assertThat(ctx).doesNotContainKey("team");

        verify(leaveBalanceService, never()).getMyBalances(anyString(), anyInt());
        verify(teamService, never()).getMyTeam(anyString());
    }

    @Test
    void hrManager_doesNotIncludeForbiddenFields() {
        Jwt jwt = jwt("kc-hr");
        User user = userWithPerson(TypeRole.HR_MANAGER, "Fatma", "Amiri");
        when(authenticatedUserResolver.require(jwt)).thenReturn(user);
        RequestActionSummary summary = RequestActionSummary.of(0, 0, 0, 0, 0, 0);
        when(dashboardRequestSummaryService.getHrRequestActionSummary()).thenReturn(summary);

        Map<String, Object> ctx = contextBuilder.build(jwt);

        assertThat(ctx).doesNotContainKey("keycloakId");
        assertThat(ctx).doesNotContainKey("salary");
        assertThat(ctx).doesNotContainKey("password");
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private Jwt jwt(String subject) {
        return new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of("sub", subject, "preferred_username", subject)
        );
    }

    private User userWithPerson(TypeRole role, String firstName, String lastName) {
        User user = new User("kc-id", "username");
        user.setRole(role);
        user.setActive(true);

        Person person = new Person(lastName, firstName, firstName.toLowerCase() + "@example.com");
        user.setPerson(person);
        return user;
    }

    private void stubLeaveBalance(String keycloakId, int annualDays) {
        LeaveBalanceDto dto = new LeaveBalanceDto();
        dto.setLeaveType(LeaveType.ANNUAL);
        dto.setAvailableDays(BigDecimal.valueOf(annualDays));
        dto.setBalanceManaged(true);

        when(leaveBalanceService.getMyBalances(keycloakId, java.time.LocalDate.now().getYear()))
                .thenReturn(List.of(dto));
    }

    private void stubEmployeeRequests(String keycloakId, long total, long leavesPending) {
        RequestActionSummary summary = new RequestActionSummary(
                total, leavesPending, 0, 0, 0, 0, 0
        );
        when(dashboardRequestSummaryService.getEmployeeOpenRequestsSummary(keycloakId))
                .thenReturn(summary);
    }

    private void stubTeam(String keycloakId, String name, int memberCount) {
        Map<String, Object> teamMap = Map.of(
                "name", name,
                "memberCount", memberCount
        );
        when(teamService.getMyTeam(keycloakId)).thenReturn(teamMap);
    }
}
