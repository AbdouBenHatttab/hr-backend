package tn.isetbizerte.pfe.hrbackend.modules.hr.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;
import tn.isetbizerte.pfe.hrbackend.common.exception.BadRequestException;
import tn.isetbizerte.pfe.hrbackend.infrastructure.kafka.producer.KafkaEventProducer;
import tn.isetbizerte.pfe.hrbackend.modules.department.entity.Department;
import tn.isetbizerte.pfe.hrbackend.modules.department.service.DepartmentService;
import tn.isetbizerte.pfe.hrbackend.modules.hr.dto.CompleteUserSetupRequest;
import tn.isetbizerte.pfe.hrbackend.modules.jobtitle.entity.JobTitle;
import tn.isetbizerte.pfe.hrbackend.modules.jobtitle.service.JobTitleService;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.StoredEmployeeDocumentRepository;
import tn.isetbizerte.pfe.hrbackend.modules.team.entity.Team;
import tn.isetbizerte.pfe.hrbackend.modules.team.repository.TeamRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.Person;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.service.EmploymentSalaryService;
import tn.isetbizerte.pfe.hrbackend.modules.user.service.UserService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class HRServiceCompleteUserSetupTest {

    private UserService userService;
    private KeycloakAdminService keycloakAdminService;
    private TeamRepository teamRepository;
    private DepartmentService departmentService;
    private JobTitleService jobTitleService;
    private EmploymentSalaryService employmentSalaryService;
    private HRService service;

    private Department department;
    private JobTitle jobTitle;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        keycloakAdminService = mock(KeycloakAdminService.class);
        teamRepository = mock(TeamRepository.class);
        departmentService = mock(DepartmentService.class);
        jobTitleService = mock(JobTitleService.class);
        employmentSalaryService = mock(EmploymentSalaryService.class);

        service = new HRService(
                userService,
                keycloakAdminService,
                mock(KafkaEventProducer.class),
                mock(StoredEmployeeDocumentRepository.class),
                teamRepository,
                departmentService,
                jobTitleService,
                employmentSalaryService
        );

        department = department(1L, "Engineering");
        jobTitle = jobTitle(2L, "Developer");
        when(departmentService.requireDepartmentForEmployment(1L)).thenReturn(department);
        when(jobTitleService.requireJobTitleForEmployment(2L)).thenReturn(jobTitle);
        when(keycloakAdminService.assignRoleToUser(anyString(), anyString())).thenReturn(true);
        when(keycloakAdminService.setUserEnabled(anyString(), anyBoolean())).thenReturn(true);
        when(employmentSalaryService.resolveEffectiveSalary(TypeRole.EMPLOYEE)).thenReturn(new BigDecimal("2500"));
        when(employmentSalaryService.resolveEffectiveSalary(TypeRole.TEAM_LEADER)).thenReturn(new BigDecimal("4000"));
        when(employmentSalaryService.resolveEffectiveSalary(TypeRole.HR_MANAGER)).thenReturn(new BigDecimal("3500"));
    }

    @Test
    void employeeSetup_savesRoleEmploymentSalaryAndTeam() {
        User user = user(10L, "new-employee", TypeRole.NEW_USER, false);
        Team team = team(3L, "Product", user(20L, "leader", TypeRole.TEAM_LEADER, true));
        when(userService.findById(10L)).thenReturn(Optional.of(user));
        when(teamRepository.existsByTeamLeader(user)).thenReturn(false);
        when(teamRepository.findByIdWithDetails(3L)).thenReturn(Optional.of(team));

        Map<String, Object> response = service.completeUserSetup(10L, request("EMPLOYEE", 3L, null), "kc-hr", "hr");

        assertThat(response).containsEntry("success", true).containsEntry("newRole", "EMPLOYEE");
        assertThat(user.getRole()).isEqualTo(TypeRole.EMPLOYEE);
        assertThat(user.getActive()).isTrue();
        assertThat(user.getTeam()).isSameAs(team);
        assertThat(user.getPerson().getDepartmentRef()).isSameAs(department);
        assertThat(user.getPerson().getJobTitleRef()).isSameAs(jobTitle);
        assertThat(user.getPerson().getHireDate()).isEqualTo(LocalDate.of(2026, 5, 2));
        assertThat(user.getPerson().getSalary()).isEqualByComparingTo("2500");
        verify(keycloakAdminService).assignRoleToUser("kc-new-employee", "EMPLOYEE");
        verify(keycloakAdminService).setUserEnabled("kc-new-employee", true);
        verify(userService).saveUser(user);
        verify(userService).savePerson(user.getPerson());
    }

    @Test
    void teamLeaderSetup_savesRoleEmploymentSalaryAndLedTeamWithoutUserTeam() {
        User user = user(11L, "new-leader", TypeRole.NEW_USER, false);
        Team ledTeam = team(4L, "Platform", null);
        when(userService.findById(11L)).thenReturn(Optional.of(user));
        when(teamRepository.findByTeamLeader(user)).thenReturn(Optional.empty());
        when(teamRepository.findByIdWithDetails(4L)).thenReturn(Optional.of(ledTeam));

        service.completeUserSetup(11L, request("TEAM_LEADER", null, 4L), "kc-hr", "hr");

        assertThat(user.getRole()).isEqualTo(TypeRole.TEAM_LEADER);
        assertThat(user.getTeam()).isNull();
        assertThat(ledTeam.getTeamLeader()).isSameAs(user);
        assertThat(user.getPerson().getSalary()).isEqualByComparingTo("4000");
        verify(keycloakAdminService).assignRoleToUser("kc-new-leader", "TEAM_LEADER");
    }

    @Test
    void teamLeaderSetup_allowsMissingLedTeamWithoutAssigningTeam() {
        User user = user(18L, "leaderless", TypeRole.NEW_USER, false);
        when(userService.findById(18L)).thenReturn(Optional.of(user));

        Map<String, Object> response = service.completeUserSetup(18L, request("TEAM_LEADER", null, null), "kc-hr", "hr");

        assertThat(response).containsEntry("success", true)
                .containsEntry("newRole", "TEAM_LEADER")
                .containsEntry("ledTeamId", null);
        assertThat(user.getRole()).isEqualTo(TypeRole.TEAM_LEADER);
        assertThat(user.getTeam()).isNull();
        assertThat(user.getPerson().getSalary()).isEqualByComparingTo("4000");
        verify(keycloakAdminService).assignRoleToUser("kc-leaderless", "TEAM_LEADER");
        verify(teamRepository, never()).findByIdWithDetails(anyLong());
        verify(teamRepository, never()).findByTeamLeader(user);
        verify(userService).saveUser(user);
        verify(userService).savePerson(user.getPerson());
    }

    @Test
    void teamLeaderSetup_rejectsSameRoleTransitionWhenAlreadyLeader() {
        User user = user(18L, "existing-leader", TypeRole.TEAM_LEADER, true);
        when(userService.findById(18L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.completeUserSetup(18L, request("TEAM_LEADER", null, null), "kc-hr", "hr"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Role transition not allowed.");

        verify(keycloakAdminService, never()).assignRoleToUser(anyString(), anyString());
        verify(userService, never()).saveUser(any());
        verify(userService, never()).savePerson(any());
    }

    @Test
    void teamLeaderSetup_rejectsTeamWithDifferentExistingLeaderBeforeKeycloak() {
        User target = user(19L, "target-leader", TypeRole.NEW_USER, false);
        User existingLeader = user(20L, "current-leader", TypeRole.TEAM_LEADER, true);
        Team ledTeam = team(4L, "Platform", existingLeader);
        when(userService.findById(19L)).thenReturn(Optional.of(target));
        when(teamRepository.findByIdWithDetails(4L)).thenReturn(Optional.of(ledTeam));

        assertThatThrownBy(() -> service.completeUserSetup(19L, request("TEAM_LEADER", null, 4L), "kc-hr", "hr"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("This team already has a Team Leader. Use Team Management to change the leader.");

        assertThat(ledTeam.getTeamLeader()).isSameAs(existingLeader);
        verify(keycloakAdminService, never()).assignRoleToUser(anyString(), anyString());
        verify(userService, never()).saveUser(any());
    }

    @Test
    void hrManagerSetup_savesRoleEmploymentAndRequiresNoTeam() {
        User user = user(12L, "new-hr", TypeRole.NEW_USER, false);
        when(userService.findById(12L)).thenReturn(Optional.of(user));
        when(teamRepository.existsByTeamLeader(user)).thenReturn(false);

        service.completeUserSetup(12L, request("HR_MANAGER", null, null), "kc-admin", "admin");

        assertThat(user.getRole()).isEqualTo(TypeRole.HR_MANAGER);
        assertThat(user.getTeam()).isNull();
        assertThat(user.getPerson().getSalary()).isEqualByComparingTo("3500");
        verify(keycloakAdminService).assignRoleToUser("kc-new-hr", "HR_MANAGER");
    }

    @Test
    void completeUserSetup_allowsNewUserToHrManager() {
        User user = user(121L, "new-hr-alt", TypeRole.NEW_USER, false);
        when(userService.findById(121L)).thenReturn(Optional.of(user));
        when(teamRepository.existsByTeamLeader(user)).thenReturn(false);

        Map<String, Object> response = service.completeUserSetup(121L, request("HR_MANAGER", null, null), "kc-admin", "admin");

        assertThat(response).containsEntry("success", true)
                .containsEntry("newRole", "HR_MANAGER");
        assertThat(user.getRole()).isEqualTo(TypeRole.HR_MANAGER);
        verify(keycloakAdminService).assignRoleToUser("kc-new-hr-alt", "HR_MANAGER");
        verify(userService).saveUser(user);
        verify(userService).savePerson(user.getPerson());
    }

    @Test
    void completeUserSetup_blocksEmployeeToHrManager() {
        User user = user(122L, "employee-to-hr", TypeRole.EMPLOYEE, true);
        when(userService.findById(122L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.completeUserSetup(122L, request("HR_MANAGER", null, null), "kc-hr", "hr"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Role transition not allowed.");

        verify(keycloakAdminService, never()).assignRoleToUser(anyString(), anyString());
        verify(userService, never()).saveUser(any());
        verify(userService, never()).savePerson(any());
    }

    @Test
    void completeUserSetup_blocksTeamLeaderToHrManager() {
        User user = user(123L, "leader-to-hr", TypeRole.TEAM_LEADER, true);
        when(userService.findById(123L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.completeUserSetup(123L, request("HR_MANAGER", null, null), "kc-hr", "hr"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Role transition not allowed.");

        verify(keycloakAdminService, never()).assignRoleToUser(anyString(), anyString());
        verify(userService, never()).saveUser(any());
        verify(userService, never()).savePerson(any());
    }

    @Test
    void completeUserSetup_blocksHrManagerToEmployee() {
        User user = user(124L, "hr-to-employee", TypeRole.HR_MANAGER, true);
        when(userService.findById(124L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.completeUserSetup(124L, request("EMPLOYEE", 3L, null), "kc-hr2", "hr2"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Role transition not allowed.");

        verify(keycloakAdminService, never()).assignRoleToUser(anyString(), anyString());
        verify(userService, never()).saveUser(any());
        verify(userService, never()).savePerson(any());
    }

    @Test
    void completeUserSetup_blocksHrManagerToTeamLeader() {
        User user = user(125L, "hr-to-leader", TypeRole.HR_MANAGER, true);
        when(userService.findById(125L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.completeUserSetup(125L, request("TEAM_LEADER", null, 4L), "kc-hr2", "hr2"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Role transition not allowed.");

        verify(keycloakAdminService, never()).assignRoleToUser(anyString(), anyString());
        verify(userService, never()).saveUser(any());
        verify(userService, never()).savePerson(any());
    }

    @Test
    void completeUserSetup_blocksSelfRoleChange() {
        User user = user(126L, "self", TypeRole.EMPLOYEE, true);
        when(userService.findById(126L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.completeUserSetup(126L, request("TEAM_LEADER", null, null), "kc-self", "self"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("You cannot change your own role or HR-managed setup.");

        verify(keycloakAdminService, never()).assignRoleToUser(anyString(), anyString());
        verify(userService, never()).saveUser(any());
        verify(userService, never()).savePerson(any());
    }

    @Test
    void completeUserSetup_allowsEmployeeToTeamLeaderWhenNotTeamMember() {
        User user = user(127L, "employee-to-leader", TypeRole.EMPLOYEE, true);
        when(userService.findById(127L)).thenReturn(Optional.of(user));
        when(teamRepository.existsByTeamLeader(user)).thenReturn(false);

        Map<String, Object> response = service.completeUserSetup(127L, request("TEAM_LEADER", null, null), "kc-hr", "hr");

        assertThat(response).containsEntry("success", true)
                .containsEntry("newRole", "TEAM_LEADER")
                .containsEntry("ledTeamId", null);
        assertThat(user.getRole()).isEqualTo(TypeRole.TEAM_LEADER);
        assertThat(user.getTeam()).isNull();
        verify(keycloakAdminService).assignRoleToUser("kc-employee-to-leader", "TEAM_LEADER");
        verify(userService).saveUser(user);
        verify(userService).savePerson(user.getPerson());
    }

    @Test
    void completeUserSetup_allowsTeamLeaderToEmployeeWhenNotLeadingTeam() {
        User user = user(128L, "leader-to-employee", TypeRole.TEAM_LEADER, true);
        Team team = team(3L, "Support", user(200L, "other-leader", TypeRole.TEAM_LEADER, true));
        when(userService.findById(128L)).thenReturn(Optional.of(user));
        when(teamRepository.existsByTeamLeader(user)).thenReturn(false);
        when(teamRepository.findByIdWithDetails(3L)).thenReturn(Optional.of(team));

        Map<String, Object> response = service.completeUserSetup(128L, request("EMPLOYEE", 3L, null), "kc-hr", "hr");

        assertThat(response).containsEntry("success", true)
                .containsEntry("newRole", "EMPLOYEE")
                .containsEntry("teamId", 3L);
        assertThat(user.getRole()).isEqualTo(TypeRole.EMPLOYEE);
        assertThat(user.getTeam()).isSameAs(team);
        verify(keycloakAdminService).assignRoleToUser("kc-leader-to-employee", "EMPLOYEE");
        verify(userService).saveUser(user);
        verify(userService).savePerson(user.getPerson());
    }

    @Test
    void setup_rejectsNewUserTargetRoleBeforeKeycloak() {
        User user = user(13L, "target", TypeRole.NEW_USER, false);
        when(userService.findById(13L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.completeUserSetup(13L, request("NEW_USER", null, null), "kc-hr", "hr"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("NEW_USER is not a valid target role for setup.");

        verify(keycloakAdminService, never()).assignRoleToUser(anyString(), anyString());
    }

    @Test
    void setup_rejectsMissingAndForbiddenRoleFieldsBeforeKeycloak() {
        User employee = user(14L, "employee", TypeRole.NEW_USER, false);
        when(userService.findById(14L)).thenReturn(Optional.of(employee));
        CompleteUserSetupRequest missingTeam = request("EMPLOYEE", null, null);

        assertThatThrownBy(() -> service.completeUserSetup(14L, missingTeam, "kc-hr", "hr"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Team is required for EMPLOYEE setup.");

        CompleteUserSetupRequest forbiddenTeam = request("HR_MANAGER", 3L, null);
        assertThatThrownBy(() -> service.completeUserSetup(14L, forbiddenTeam, "kc-hr", "hr"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("HR_MANAGER setup must not include teamId or ledTeamId.");

        verify(keycloakAdminService, never()).assignRoleToUser(anyString(), anyString());
    }

    @Test
    void setup_rejectsInvalidTeamStatesBeforeKeycloak() {
        User employee = user(15L, "employee", TypeRole.NEW_USER, false);
        Team teamWithoutLeader = team(3L, "No Leader", null);
        when(userService.findById(15L)).thenReturn(Optional.of(employee));
        when(teamRepository.existsByTeamLeader(employee)).thenReturn(false);
        when(teamRepository.findByIdWithDetails(3L)).thenReturn(Optional.of(teamWithoutLeader));

        assertThatThrownBy(() -> service.completeUserSetup(15L, request("EMPLOYEE", 3L, null), "kc-hr", "hr"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Target team must have a Team Leader before assigning members.");

        User leader = user(16L, "leader", TypeRole.TEAM_LEADER, true);
        Team currentLedTeam = team(9L, "Current", leader);
        when(userService.findById(16L)).thenReturn(Optional.of(leader));
        when(teamRepository.existsByTeamLeader(leader)).thenReturn(true);
        when(teamRepository.findByIdWithDetails(4L)).thenReturn(Optional.of(team(4L, "Other", user(21L, "other-leader", TypeRole.TEAM_LEADER, true))));
        when(teamRepository.findByTeamLeader(leader)).thenReturn(Optional.of(currentLedTeam));

        assertThatThrownBy(() -> service.completeUserSetup(16L, request("EMPLOYEE", 4L, null), "kc-hr", "hr"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("This user currently leads a team. Reassign or remove team leadership before assigning EMPLOYEE.");

        verify(keycloakAdminService, never()).assignRoleToUser(anyString(), anyString());
    }

    @Test
    void readinessNoLongerReportsEmploymentOrTeamIssuesAfterEmployeeSetup() {
        User user = user(17L, "ready", TypeRole.NEW_USER, false);
        Team team = team(3L, "Product", user(20L, "leader", TypeRole.TEAM_LEADER, true));
        when(userService.findById(17L)).thenReturn(Optional.of(user));
        when(teamRepository.existsByTeamLeader(user)).thenReturn(false);
        when(teamRepository.findByIdWithDetails(3L)).thenReturn(Optional.of(team));

        service.completeUserSetup(17L, request("EMPLOYEE", 3L, null), "kc-hr", "hr");
        when(userService.getAllUsers()).thenReturn(List.of(user));

        Map<String, Object> details = service.getAllUsersWithDetails().get(0);

        assertThat((List<String>) details.get("setupIssues"))
                .doesNotContain("MISSING_DEPARTMENT", "MISSING_JOB_TITLE", "MISSING_HIRE_DATE", "MISSING_TEAM");
    }

    private CompleteUserSetupRequest request(String role, Long teamId, Long ledTeamId) {
        CompleteUserSetupRequest request = new CompleteUserSetupRequest();
        request.setRole(role);
        request.setDepartmentId(1L);
        request.setJobTitleId(2L);
        request.setHireDate(LocalDate.of(2026, 5, 2));
        request.setTeamId(teamId);
        request.setLedTeamId(ledTeamId);
        return request;
    }

    private User user(Long id, String username, TypeRole role, boolean active) {
        User user = new User("kc-" + username, username);
        user.setId(id);
        user.setRole(role);
        user.setActive(active);
        user.setPerson(new Person("Last", "First", username + "@example.com"));
        return user;
    }

    private Team team(Long id, String name, User leader) {
        Team team = new Team();
        team.setId(id);
        team.setName(name);
        team.setTeamLeader(leader);
        return team;
    }

    private Department department(Long id, String name) {
        Department department = new Department();
        department.setId(id);
        department.setName(name);
        department.setActive(true);
        return department;
    }

    private JobTitle jobTitle(Long id, String name) {
        JobTitle jobTitle = new JobTitle();
        jobTitle.setId(id);
        jobTitle.setName(name);
        jobTitle.setActive(true);
        return jobTitle;
    }
}
