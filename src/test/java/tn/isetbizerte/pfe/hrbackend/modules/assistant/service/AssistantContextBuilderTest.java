package tn.isetbizerte.pfe.hrbackend.modules.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AssistantContextBuilder.
 *
 * All dependencies are Mockito mocks — no Spring context needed.
 *
 * Tests verify:
 *  1. EMPLOYEE receives employee context only (no team, no hr).
 *  2. TEAM_LEADER receives employee + team context (no hr).
 *  3. HR_MANAGER receives hr context only (no employee, no team).
 *  4. HR_MANAGER does NOT trigger leave balance or team service calls.
 *  5. Serialized SafeAssistantContext never contains forbidden keys.
 *  6. Missing Person profile does not crash.
 *  7. Optional service failure does not crash the full context build.
 *  8. TEAM_LEADER with no team assigned does not throw.
 *  9. Full request breakdown fields are populated correctly.
 * 10. HR context carries all breakdown fields + newUsersPendingApproval.
 */
class AssistantContextBuilderTest {

    // --- Mocks ---
    private AuthenticatedUserResolver authenticatedUserResolver;
    private LeaveBalanceService leaveBalanceService;
    private DashboardRequestSummaryService dashboardRequestSummaryService;
    private TeamRepository teamRepository;
    private LeaveRequestRepository leaveRequestRepository;
    private UserRepository userRepository;

    private AssistantContextBuilder contextBuilder;

    // Jackson for serialization leak tests
    private final ObjectMapper mapper = new ObjectMapper();

    /** Forbidden JSON keys that must never appear anywhere in a serialized context. */
    private static final List<String> FORBIDDEN_KEYS = List.of(
            "salary", "currentMonthlyDeductions", "phone", "address",
            "birthDate", "maritalStatus", "numberOfChildren", "avatarPhoto",
            "keycloakId", "email", "password", "loginHistories",
            "members", "personalInfo"
    );

    @BeforeEach
    void setUp() {
        authenticatedUserResolver      = mock(AuthenticatedUserResolver.class);
        leaveBalanceService            = mock(LeaveBalanceService.class);
        dashboardRequestSummaryService = mock(DashboardRequestSummaryService.class);
        teamRepository                 = mock(TeamRepository.class);
        leaveRequestRepository         = mock(LeaveRequestRepository.class);
        userRepository                 = mock(UserRepository.class);

        contextBuilder = new AssistantContextBuilder(
                authenticatedUserResolver,
                leaveBalanceService,
                dashboardRequestSummaryService,
                teamRepository,
                leaveRequestRepository,
                userRepository
        );
    }

    // ===========================================================================
    // 1. EMPLOYEE context
    // ===========================================================================

    @Test
    void employee_includesDisplayName() {
        Jwt jwt = jwt("kc-emp");
        User user = userWithPerson(TypeRole.EMPLOYEE, "Ahmed", "Ben Ali");
        when(authenticatedUserResolver.require(jwt)).thenReturn(user);
        stubLeaveBalances("kc-emp", 12, 5);
        stubEmployeeRequests("kc-emp", 5, 2, 1, 0L, 1, 1);

        SafeAssistantContext ctx = contextBuilder.build(jwt);

        assertThat(ctx.displayName()).isEqualTo("Ahmed Ben Ali");
    }

    @Test
    void employee_includesFullRequestBreakdown() {
        Jwt jwt = jwt("kc-emp");
        User user = userWithPerson(TypeRole.EMPLOYEE, "Ahmed", "Ben Ali");
        when(authenticatedUserResolver.require(jwt)).thenReturn(user);
        stubLeaveBalances("kc-emp", 15, 8);
        // total=7: leaves(3) + docs(2) + docsAwaiting(0) + loans(1) + auths(1) = 7
        stubEmployeeRequests("kc-emp", 7, 3, 2, 0L, 1, 1);

        SafeAssistantContext ctx = contextBuilder.build(jwt);

        assertThat(ctx.employee()).isNotNull();
        assertThat(ctx.employee().annualAvailableDays()).isEqualTo(15);
        assertThat(ctx.employee().sickAvailableDays()).isEqualTo(8);
        assertThat(ctx.employee().totalPendingRequests()).isEqualTo(7L);
        assertThat(ctx.employee().leavesPending()).isEqualTo(3L);
        assertThat(ctx.employee().documentsPending()).isEqualTo(2L);
        assertThat(ctx.employee().documentsAwaitingFile()).isEqualTo(0L);
        assertThat(ctx.employee().loansPending()).isEqualTo(1L);
        assertThat(ctx.employee().authorizationsPending()).isEqualTo(1L);
    }

    @Test
    void employee_doesNotIncludeTeamOrHrContext() {
        Jwt jwt = jwt("kc-emp");
        User user = userWithPerson(TypeRole.EMPLOYEE, "Ahmed", "Ben Ali");
        when(authenticatedUserResolver.require(jwt)).thenReturn(user);
        stubLeaveBalances("kc-emp", 10, 5);
        stubEmployeeRequests("kc-emp", 0, 0, 0, 0L, 0, 0);

        SafeAssistantContext ctx = contextBuilder.build(jwt);

        assertThat(ctx.team()).isNull();
        assertThat(ctx.hr()).isNull();

        verify(teamRepository, never()).findByTeamLeaderKeycloakId(anyString());
        verify(dashboardRequestSummaryService, never()).getHrRequestActionSummary();
        verify(userRepository, never()).countByRole(any());
    }

    @Test
    void employee_serializedContext_containsNoForbiddenKeys() throws Exception {
        Jwt jwt = jwt("kc-emp");
        User user = userWithPerson(TypeRole.EMPLOYEE, "Ahmed", "Ben Ali");
        when(authenticatedUserResolver.require(jwt)).thenReturn(user);
        stubLeaveBalances("kc-emp", 10, 5);
        stubEmployeeRequests("kc-emp", 0, 0, 0, 0L, 0, 0);

        SafeAssistantContext ctx = contextBuilder.build(jwt);
        String json = mapper.writeValueAsString(ctx);

        for (String forbidden : FORBIDDEN_KEYS) {
            assertThat(json)
                    .as("Forbidden key '%s' must not appear in serialized EMPLOYEE context", forbidden)
                    .doesNotContain("\"" + forbidden + "\"");
        }
    }

    // ===========================================================================
    // 2. TEAM_LEADER context
    // ===========================================================================

    @Test
    void teamLeader_includesEmployeeAndTeamContext() {
        Jwt jwt = jwt("kc-lead");
        User user = userWithPerson(TypeRole.TEAM_LEADER, "Sami", "Trabelsi");
        when(authenticatedUserResolver.require(jwt)).thenReturn(user);
        stubLeaveBalances("kc-lead", 8, 3);
        stubEmployeeRequests("kc-lead", 2, 1, 0, 0L, 0, 1);
        stubTeam("kc-lead", 42L, "Backend Squad", 5, 3);

        SafeAssistantContext ctx = contextBuilder.build(jwt);

        // Employee section
        assertThat(ctx.employee()).isNotNull();
        assertThat(ctx.employee().annualAvailableDays()).isEqualTo(8);
        assertThat(ctx.employee().sickAvailableDays()).isEqualTo(3);

        // Team section
        assertThat(ctx.team()).isNotNull();
        assertThat(ctx.team().teamName()).isEqualTo("Backend Squad");
        assertThat(ctx.team().memberCount()).isEqualTo(5);
        assertThat(ctx.team().pendingTeamLeaderApprovals()).isEqualTo(3L);

        // HR section must be absent
        assertThat(ctx.hr()).isNull();
    }

    @Test
    void teamLeader_withNoTeamAssigned_doesNotFailAndTeamSubFieldsAreNull() {
        Jwt jwt = jwt("kc-lead");
        User user = userWithPerson(TypeRole.TEAM_LEADER, "Sami", "Trabelsi");
        when(authenticatedUserResolver.require(jwt)).thenReturn(user);
        stubLeaveBalances("kc-lead", 8, 3);
        stubEmployeeRequests("kc-lead", 0, 0, 0, 0L, 0, 0);
        when(teamRepository.findByTeamLeaderKeycloakId("kc-lead")).thenReturn(Optional.empty());

        SafeAssistantContext ctx = contextBuilder.build(jwt);

        assertThat(ctx.team()).isNotNull();   // TeamContext object is still returned
        assertThat(ctx.team().teamName()).isNull();
        assertThat(ctx.team().memberCount()).isNull();
        assertThat(ctx.team().pendingTeamLeaderApprovals()).isEqualTo(0L);

        // Employee context still populated
        assertThat(ctx.employee()).isNotNull();
    }

    @Test
    void teamLeader_serializedContext_containsNoForbiddenKeys() throws Exception {
        Jwt jwt = jwt("kc-lead");
        User user = userWithPerson(TypeRole.TEAM_LEADER, "Sami", "Trabelsi");
        when(authenticatedUserResolver.require(jwt)).thenReturn(user);
        stubLeaveBalances("kc-lead", 8, 3);
        stubEmployeeRequests("kc-lead", 2, 1, 0, 0L, 0, 1);
        stubTeam("kc-lead", 42L, "Backend Squad", 5, 2);

        SafeAssistantContext ctx = contextBuilder.build(jwt);
        String json = mapper.writeValueAsString(ctx);

        for (String forbidden : FORBIDDEN_KEYS) {
            assertThat(json)
                    .as("Forbidden key '%s' must not appear in serialized TEAM_LEADER context", forbidden)
                    .doesNotContain("\"" + forbidden + "\"");
        }
    }

    // ===========================================================================
    // 3 & 4. HR_MANAGER context
    // ===========================================================================

    @Test
    void hrManager_includesHrContextWithFullBreakdown() {
        Jwt jwt = jwt("kc-hr");
        User user = userWithPerson(TypeRole.HR_MANAGER, "Fatma", "Amiri");
        when(authenticatedUserResolver.require(jwt)).thenReturn(user);
        // total=21: leaves(5) + docs(3) + docsAwaiting(2) + loans(4) + loansAwaiting(2) + auths(5) = 21
        stubHrRequests(21, 5, 3, 2L, 4, 2, 5);
        when(userRepository.countByRole(TypeRole.NEW_USER)).thenReturn(4L);

        SafeAssistantContext ctx = contextBuilder.build(jwt);

        assertThat(ctx.hr()).isNotNull();
        assertThat(ctx.hr().totalPendingActions()).isEqualTo(21L);
        assertThat(ctx.hr().leavesPending()).isEqualTo(5L);
        // documentsPending = PENDING status only, NOT merged with documentsAwaitingFile
        assertThat(ctx.hr().documentsPending()).isEqualTo(3L);
        // documentsAwaitingFile exposed separately — approved but file not yet uploaded
        assertThat(ctx.hr().documentsAwaitingFile()).isEqualTo(2L);
        // loansPending merges loansPending + loansAwaitingFile (indistinguishable to HR)
        assertThat(ctx.hr().loansPending()).isEqualTo(4L + 2L);
        assertThat(ctx.hr().authorizationsPending()).isEqualTo(5L);
        assertThat(ctx.hr().newUsersPendingApproval()).isEqualTo(4L);
    }

    @Test
    void hrManager_doesNotIncludeLeaveOrTeamContext() {
        Jwt jwt = jwt("kc-hr");
        User user = userWithPerson(TypeRole.HR_MANAGER, "Fatma", "Amiri");
        when(authenticatedUserResolver.require(jwt)).thenReturn(user);
        stubHrRequests(0, 0, 0, 0L, 0, 0, 0);
        when(userRepository.countByRole(TypeRole.NEW_USER)).thenReturn(0L);

        SafeAssistantContext ctx = contextBuilder.build(jwt);

        assertThat(ctx.employee()).isNull();
        assertThat(ctx.team()).isNull();

        verify(leaveBalanceService, never()).getMyBalances(anyString(), anyInt());
        verify(teamRepository, never()).findByTeamLeaderKeycloakId(anyString());
    }

    @Test
    void hrManager_serializedContext_containsNoForbiddenKeys() throws Exception {
        Jwt jwt = jwt("kc-hr");
        User user = userWithPerson(TypeRole.HR_MANAGER, "Fatma", "Amiri");
        when(authenticatedUserResolver.require(jwt)).thenReturn(user);
        stubHrRequests(10, 3, 2, 0L, 2, 1, 2);
        when(userRepository.countByRole(TypeRole.NEW_USER)).thenReturn(2L);

        SafeAssistantContext ctx = contextBuilder.build(jwt);
        String json = mapper.writeValueAsString(ctx);

        for (String forbidden : FORBIDDEN_KEYS) {
            assertThat(json)
                    .as("Forbidden key '%s' must not appear in serialized HR_MANAGER context", forbidden)
                    .doesNotContain("\"" + forbidden + "\"");
        }
    }

    // ===========================================================================
    // 5. Missing Person — must not crash
    // ===========================================================================

    @Test
    void employee_missingPersonProfile_doesNotCrash() {
        Jwt jwt = jwt("kc-emp");
        User user = new User("kc-emp", "emp-no-person");
        user.setRole(TypeRole.EMPLOYEE);
        user.setActive(true);
        // No Person set — simulates newly registered user whose profile is incomplete
        when(authenticatedUserResolver.require(jwt)).thenReturn(user);
        stubLeaveBalances("kc-emp", 10, 5);
        stubEmployeeRequests("kc-emp", 0, 0, 0, 0L, 0, 0);

        SafeAssistantContext ctx = contextBuilder.build(jwt);

        assertThat(ctx.displayName()).isNull();   // safe null, not an exception
        assertThat(ctx.employee()).isNotNull();    // employee context still built
    }

    @Test
    void employee_missingFirstAndLastName_displayNameIsNull() {
        Jwt jwt = jwt("kc-emp");
        User user = new User("kc-emp", "emp");
        user.setRole(TypeRole.EMPLOYEE);
        user.setActive(true);
        Person person = new Person();            // no firstName, no lastName
        person.setEmail("anon@example.com");    // email must NOT be used as display name
        user.setPerson(person);
        when(authenticatedUserResolver.require(jwt)).thenReturn(user);
        stubLeaveBalances("kc-emp", 0, 0);
        stubEmployeeRequests("kc-emp", 0, 0, 0, 0L, 0, 0);

        SafeAssistantContext ctx = contextBuilder.build(jwt);

        assertThat(ctx.displayName()).isNull();
    }

    // ===========================================================================
    // 6. Optional service failure — partial context still built
    // ===========================================================================

    @Test
    void employee_leaveServiceFailure_requestContextStillPresent() {
        Jwt jwt = jwt("kc-emp");
        User user = userWithPerson(TypeRole.EMPLOYEE, "Ahmed", "Ben Ali");
        when(authenticatedUserResolver.require(jwt)).thenReturn(user);

        // Leave service throws — request context should still be populated
        when(leaveBalanceService.getMyBalances(anyString(), anyInt()))
                .thenThrow(new RuntimeException("DB connection lost"));
        stubEmployeeRequests("kc-emp", 3, 2, 0, 0L, 0, 1);

        SafeAssistantContext ctx = contextBuilder.build(jwt);

        assertThat(ctx.employee()).isNotNull();
        assertThat(ctx.employee().annualAvailableDays()).isNull();  // not available
        assertThat(ctx.employee().sickAvailableDays()).isNull();    // not available
        assertThat(ctx.employee().totalPendingRequests()).isEqualTo(3L);
        assertThat(ctx.employee().leavesPending()).isEqualTo(2L);
    }

    @Test
    void employee_requestSummaryFailure_leaveBalanceStillPresent() {
        Jwt jwt = jwt("kc-emp");
        User user = userWithPerson(TypeRole.EMPLOYEE, "Ahmed", "Ben Ali");
        when(authenticatedUserResolver.require(jwt)).thenReturn(user);
        stubLeaveBalances("kc-emp", 12, 5);

        // Request summary throws — leave context should still be populated
        when(dashboardRequestSummaryService.getEmployeeOpenRequestsSummary(anyString()))
                .thenThrow(new RuntimeException("Timeout"));

        SafeAssistantContext ctx = contextBuilder.build(jwt);

        assertThat(ctx.employee()).isNotNull();
        assertThat(ctx.employee().annualAvailableDays()).isEqualTo(12);
        assertThat(ctx.employee().totalPendingRequests()).isEqualTo(0L); // defaulted to 0
    }

    @Test
    void teamLeader_teamContextFailure_employeeContextStillPresent() {
        Jwt jwt = jwt("kc-lead");
        User user = userWithPerson(TypeRole.TEAM_LEADER, "Sami", "Trabelsi");
        when(authenticatedUserResolver.require(jwt)).thenReturn(user);
        stubLeaveBalances("kc-lead", 8, 3);
        stubEmployeeRequests("kc-lead", 1, 1, 0, 0L, 0, 0);

        // Team repository throws an unexpected error
        when(teamRepository.findByTeamLeaderKeycloakId(anyString()))
                .thenThrow(new RuntimeException("DB timeout"));

        SafeAssistantContext ctx = contextBuilder.build(jwt);

        // Team context falls back gracefully
        assertThat(ctx.team()).isNotNull();
        assertThat(ctx.team().teamName()).isNull();
        // Employee context is unaffected
        assertThat(ctx.employee()).isNotNull();
        assertThat(ctx.employee().annualAvailableDays()).isEqualTo(8);
    }

    @Test
    void hrManager_requestSummaryFailure_returnsZeroCountsNotNull() {
        Jwt jwt = jwt("kc-hr");
        User user = userWithPerson(TypeRole.HR_MANAGER, "Fatma", "Amiri");
        when(authenticatedUserResolver.require(jwt)).thenReturn(user);

        when(dashboardRequestSummaryService.getHrRequestActionSummary())
                .thenThrow(new RuntimeException("Service down"));
        when(userRepository.countByRole(TypeRole.NEW_USER)).thenReturn(2L);

        SafeAssistantContext ctx = contextBuilder.build(jwt);

        assertThat(ctx.hr()).isNotNull();
        assertThat(ctx.hr().totalPendingActions()).isEqualTo(0L);
        assertThat(ctx.hr().newUsersPendingApproval()).isEqualTo(2L); // still populated
    }

    // ===========================================================================
    // 7. NEW_USER — empty context (safety backstop)
    // ===========================================================================

    @Test
    void newUser_producesEmptyContext() {
        Jwt jwt = jwt("kc-new");
        User user = userWithPerson(TypeRole.NEW_USER, "New", "User");
        when(authenticatedUserResolver.require(jwt)).thenReturn(user);

        SafeAssistantContext ctx = contextBuilder.build(jwt);

        // Endpoint blocks NEW_USER; this is a pure defensive backstop
        assertThat(ctx.displayName()).isNull();
        assertThat(ctx.employee()).isNull();
        assertThat(ctx.team()).isNull();
        assertThat(ctx.hr()).isNull();

        verify(leaveBalanceService, never()).getMyBalances(anyString(), anyInt());
        verify(dashboardRequestSummaryService, never()).getEmployeeOpenRequestsSummary(anyString());
        verify(dashboardRequestSummaryService, never()).getHrRequestActionSummary();
        verify(teamRepository, never()).findByTeamLeaderKeycloakId(anyString());
    }

    // ===========================================================================
    // 8. documentsAwaitingFile — explicit field regression tests
    //
    // These four tests are the regression guard for the bug where
    // documentsAwaitingFile was counted in totalPendingRequests but never
    // exposed in the context breakdown, making the AI assistant's explanation
    // inconsistent with the total.
    // ===========================================================================

    @Test
    void employee_documentsAwaitingFile_exposedSeparatelyNotMergedIntoPending() {
        // The canonical case from the bug report:
        //   documentsPending=1, documentsAwaitingFile=2, total must be 3.
        Jwt jwt = jwt("kc-emp");
        User user = userWithPerson(TypeRole.EMPLOYEE, "Ahmed", "Ben Ali");
        when(authenticatedUserResolver.require(jwt)).thenReturn(user);
        stubLeaveBalances("kc-emp", 10, 5);
        // total = leaves(0) + docs(1) + docsAwaiting(2) + loans(0) + auths(0) = 3
        stubEmployeeRequests("kc-emp", 3, 0, 1, 2L, 0, 0);

        SafeAssistantContext ctx = contextBuilder.build(jwt);

        assertThat(ctx.employee()).isNotNull();
        // totalPendingRequests includes documentsAwaitingFile
        assertThat(ctx.employee().totalPendingRequests()).isEqualTo(3L);
        // documentsPending = PENDING status only — must NOT absorb documentsAwaitingFile
        assertThat(ctx.employee().documentsPending()).isEqualTo(1L);
        // documentsAwaitingFile is the separate, explicit field
        assertThat(ctx.employee().documentsAwaitingFile()).isEqualTo(2L);
        // The AI can now reconcile: 1 + 2 = 3 — no gap
    }

    @Test
    void employee_documentsAwaitingFileZero_breakdownSumMatchesTotal() {
        // When documentsAwaitingFile is zero, nothing changes versus the old behaviour.
        Jwt jwt = jwt("kc-emp");
        User user = userWithPerson(TypeRole.EMPLOYEE, "Ahmed", "Ben Ali");
        when(authenticatedUserResolver.require(jwt)).thenReturn(user);
        stubLeaveBalances("kc-emp", 10, 5);
        // total = leaves(2) + docs(3) + docsAwaiting(0) + loans(1) + auths(0) = 6
        stubEmployeeRequests("kc-emp", 6, 2, 3, 0L, 1, 0);

        SafeAssistantContext ctx = contextBuilder.build(jwt);

        assertThat(ctx.employee().totalPendingRequests()).isEqualTo(6L);
        assertThat(ctx.employee().documentsPending()).isEqualTo(3L);
        assertThat(ctx.employee().documentsAwaitingFile()).isEqualTo(0L);
        // Sum of breakdown fields must equal total
        long breakdownSum = ctx.employee().leavesPending()
                + ctx.employee().documentsPending()
                + ctx.employee().documentsAwaitingFile()
                + ctx.employee().loansPending()
                + ctx.employee().authorizationsPending();
        assertThat(breakdownSum).isEqualTo(ctx.employee().totalPendingRequests());
    }

    @Test
    void employee_loansAwaitingFileBehaviourUnchanged() {
        // loansAwaitingFile must continue to be merged into loansPending.
        // This test is the explicit regression guard for that contract.
        Jwt jwt = jwt("kc-emp");
        User user = userWithPerson(TypeRole.EMPLOYEE, "Ahmed", "Ben Ali");
        when(authenticatedUserResolver.require(jwt)).thenReturn(user);
        stubLeaveBalances("kc-emp", 10, 5);
        // Stub: loansPending=2, loansAwaitingFile=3 in RequestActionSummary.
        // The builder must add them: EmployeeContext.loansPending = 2 + 3 = 5.
        // We set total to match: leaves(0)+docs(0)+docsAwaiting(0)+loans(2)+loansAwaitingFile(3)
        //   = 5 — which RequestActionSummary.of() would compute. We pass it directly.
        RequestActionSummary summary = new RequestActionSummary(
                5L, 0L, 0L, 0L, 2L, 3L, 0L
        );
        when(dashboardRequestSummaryService.getEmployeeOpenRequestsSummary("kc-emp"))
                .thenReturn(summary);

        SafeAssistantContext ctx = contextBuilder.build(jwt);

        // loansPending in context = loansPending(2) + loansAwaitingFile(3)
        assertThat(ctx.employee().loansPending()).isEqualTo(5L);
        // loansAwaitingFile must NOT appear as a separate field (no such field in EmployeeContext)
        // — verified implicitly by EmployeeContext having no such accessor
        assertThat(ctx.employee().totalPendingRequests()).isEqualTo(5L);
    }

    @Test
    void hrManager_documentsAwaitingFile_exposedSeparatelyNotMergedIntoPending() {
        // Mirror of the employee test — same bug existed in HrContext.
        Jwt jwt = jwt("kc-hr");
        User user = userWithPerson(TypeRole.HR_MANAGER, "Fatma", "Amiri");
        when(authenticatedUserResolver.require(jwt)).thenReturn(user);
        // total = leaves(0) + docs(1) + docsAwaiting(4) + loans(0) + loansAwaiting(0) + auths(0) = 5
        stubHrRequests(5, 0, 1, 4L, 0, 0, 0);
        when(userRepository.countByRole(TypeRole.NEW_USER)).thenReturn(0L);

        SafeAssistantContext ctx = contextBuilder.build(jwt);

        assertThat(ctx.hr()).isNotNull();
        assertThat(ctx.hr().totalPendingActions()).isEqualTo(5L);
        // documentsPending = PENDING status only
        assertThat(ctx.hr().documentsPending()).isEqualTo(1L);
        // documentsAwaitingFile is the separate, explicit field
        assertThat(ctx.hr().documentsAwaitingFile()).isEqualTo(4L);
    }

    // ===========================================================================
    // Helpers
    // ===========================================================================

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
        User user = new User("kc-id-" + role.name(), "user-" + role.name().toLowerCase());
        user.setRole(role);
        user.setActive(true);
        Person person = new Person(lastName, firstName, firstName.toLowerCase() + "@example.com");
        user.setPerson(person);
        return user;
    }

    private void stubLeaveBalances(String keycloakId, int annualDays, int sickDays) {
        LeaveBalanceDto annual = new LeaveBalanceDto();
        annual.setLeaveType(LeaveType.ANNUAL);
        annual.setAvailableDays(BigDecimal.valueOf(annualDays));
        annual.setBalanceManaged(true);

        LeaveBalanceDto sick = new LeaveBalanceDto();
        sick.setLeaveType(LeaveType.SICK);
        sick.setAvailableDays(BigDecimal.valueOf(sickDays));
        sick.setBalanceManaged(true);

        when(leaveBalanceService.getMyBalances(keycloakId, java.time.LocalDate.now().getYear()))
                .thenReturn(List.of(annual, sick));
    }

    /**
     * @param total         expected total (must equal the sum of all breakdown buckets)
     * @param leaves        leavesPending
     * @param docs          documentsPending  (PENDING status only)
     * @param docsAwaiting  documentsAwaitingFile (APPROVED, file not yet uploaded)
     * @param loans         loansPending (loansAwaitingFile is always stubbed as 0 here
     *                      because the builder merges it into loansPending)
     * @param auths         authorizationsPending
     */
    private void stubEmployeeRequests(String keycloakId, long total, long leaves,
                                      long docs, long docsAwaiting, long loans, long auths) {
        RequestActionSummary summary = new RequestActionSummary(
                total, leaves, docs, docsAwaiting, loans, 0L, auths
        );
        when(dashboardRequestSummaryService.getEmployeeOpenRequestsSummary(keycloakId))
                .thenReturn(summary);
    }

    /**
     * Stubs team repository and leave request count for a TEAM_LEADER.
     */
    private void stubTeam(String keycloakId, Long teamId, String teamName,
                          int memberCount, long pendingApprovals) {
        Team team = new Team();
        team.setId(teamId);
        team.setName(teamName);
        when(teamRepository.findByTeamLeaderKeycloakId(keycloakId)).thenReturn(Optional.of(team));
        when(userRepository.countByTeamId(teamId)).thenReturn((long) memberCount);
        when(leaveRequestRepository.countPendingTeamLeaderApprovalsByTeamId(teamId, keycloakId))
                .thenReturn(pendingApprovals);
    }

    /**
     * @param total         totalPendingActions (should equal sum of the rest)
     * @param leaves        leavesPending
     * @param docs          documentsPending  (PENDING status only)
     * @param docsAwaiting  documentsAwaitingFile (APPROVED, file not yet uploaded)
     * @param loans         loansPending
     * @param loansAwaiting loansAwaitingFile (builder merges this into loansPending)
     * @param auths         authorizationsPending
     */
    private void stubHrRequests(long total, long leaves, long docs, long docsAwaiting,
                                long loans, long loansAwaiting, long auths) {
        RequestActionSummary summary = new RequestActionSummary(
                total, leaves, docs, docsAwaiting, loans, loansAwaiting, auths
        );
        when(dashboardRequestSummaryService.getHrRequestActionSummary()).thenReturn(summary);
    }
}
