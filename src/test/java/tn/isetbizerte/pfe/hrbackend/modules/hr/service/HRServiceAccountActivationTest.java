package tn.isetbizerte.pfe.hrbackend.modules.hr.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;
import tn.isetbizerte.pfe.hrbackend.common.exception.BadRequestException;
import tn.isetbizerte.pfe.hrbackend.infrastructure.kafka.producer.KafkaEventProducer;
import tn.isetbizerte.pfe.hrbackend.modules.department.service.DepartmentService;
import tn.isetbizerte.pfe.hrbackend.modules.jobtitle.service.JobTitleService;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.StoredEmployeeDocumentRepository;
import tn.isetbizerte.pfe.hrbackend.modules.team.entity.Team;
import tn.isetbizerte.pfe.hrbackend.modules.team.repository.TeamRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.service.EmploymentSalaryService;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.service.UserService;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class HRServiceAccountActivationTest {

    private static final String SETUP_FLOW_REQUIRED_MESSAGE =
            "Use the HR setup flow to assign or change roles with required employment and team setup.";

    private UserService userService;
    private KeycloakAdminService keycloakAdminService;
    private TeamRepository teamRepository;
    private HRService service;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        keycloakAdminService = mock(KeycloakAdminService.class);
        teamRepository = mock(TeamRepository.class);
        service = new HRService(
                userService,
                keycloakAdminService,
                mock(KafkaEventProducer.class),
                mock(StoredEmployeeDocumentRepository.class),
                teamRepository,
                mock(DepartmentService.class),
                mock(JobTitleService.class),
                mock(EmploymentSalaryService.class)
        );
    }

    @Test
    void deactivateUser_blocksHrManagerFromDeactivatingOwnAccount() {
        User hr = user(1L, "hr", TypeRole.HR_MANAGER, true);
        when(userService.findById(1L)).thenReturn(Optional.of(hr));

        assertThatThrownBy(() -> service.deactivateUser(1L, "hr"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("You cannot change your own account activation status.");

        verify(keycloakAdminService, never()).setUserEnabled(anyString(), anyBoolean());
        verify(userService, never()).saveUser(any());
    }

    @Test
    void activateUser_blocksHrManagerFromReactivatingOwnAccount() {
        User hr = user(1L, "hr", TypeRole.HR_MANAGER, false);
        when(userService.findById(1L)).thenReturn(Optional.of(hr));

        assertThatThrownBy(() -> service.activateUser(1L, "kc-hr", "hr"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("You cannot change your own account activation status.");

        verify(keycloakAdminService, never()).setUserEnabled(anyString(), anyBoolean());
        verify(userService, never()).saveUser(any());
    }

    @Test
    void deactivateAndReactivateUser_updatesKeycloakAndLocalActiveFlag() {
        User employee = user(2L, "employee", TypeRole.EMPLOYEE, true);
        when(userService.findById(2L)).thenReturn(Optional.of(employee));
        when(keycloakAdminService.setUserEnabled("kc-employee", false)).thenReturn(true);
        when(keycloakAdminService.setUserEnabled("kc-employee", true)).thenReturn(true);

        Map<String, Object> deactivated = service.deactivateUser(2L, "hr");

        assertThat(deactivated).containsEntry("active", false);
        assertThat(employee.getActive()).isFalse();
        verify(userService).saveUser(employee);

        Map<String, Object> activated = service.activateUser(2L, "hr");

        assertThat(activated).containsEntry("active", true);
        assertThat(employee.getActive()).isTrue();
        verify(keycloakAdminService).setUserEnabled("kc-employee", false);
        verify(keycloakAdminService).setUserEnabled("kc-employee", true);
        verify(userService, times(2)).saveUser(employee);
    }

    @Test
    void assignRole_rejectsEmployeeRoleThroughLegacyEndpointBeforeKeycloak() {
        assertLegacyRoleChangeRejected(TypeRole.EMPLOYEE);
    }

    @Test
    void assignRole_rejectsTeamLeaderRoleThroughLegacyEndpointBeforeKeycloak() {
        assertLegacyRoleChangeRejected(TypeRole.TEAM_LEADER);
    }

    @Test
    void assignRole_rejectsHrManagerRoleThroughLegacyEndpointBeforeKeycloak() {
        assertLegacyRoleChangeRejected(TypeRole.HR_MANAGER);
    }

    @Test
    void assignRole_rejectsLeadingTeamLeaderDemotionToNewUser() {
        assertLeadingTeamLeaderDemotionRejected(TypeRole.NEW_USER);
    }

    @Test
    void assignRole_rejectsNonLeadingTeamLeaderNormalRoleChange() {
        User leader = user(3L, "leader", TypeRole.TEAM_LEADER, true);

        assertThatThrownBy(() -> service.assignRoleToUser(3L, "EMPLOYEE", "hr"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(SETUP_FLOW_REQUIRED_MESSAGE);

        assertThat(leader.getRole()).isEqualTo(TypeRole.TEAM_LEADER);
        verify(keycloakAdminService, never()).assignRoleToUser(anyString(), anyString());
        verify(userService, never()).saveUser(any());
    }

    @Test
    void assignRole_rejectsSelfRoleChange() {
        User hr = user(1L, "hr", TypeRole.HR_MANAGER, true);
        when(userService.findById(1L)).thenReturn(Optional.of(hr));

        assertThatThrownBy(() -> service.assignRoleToUser(1L, "NEW_USER", "hr"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("You cannot change your own role.");

        verify(keycloakAdminService, never()).assignRoleToUser(anyString(), anyString());
        verify(userService, never()).saveUser(any());
    }

    @Test
    void assignRole_rejectsHrManagerNormalRoleChange() {
        User employee = user(4L, "employee", TypeRole.EMPLOYEE, true);

        assertThatThrownBy(() -> service.assignRoleToUser(4L, "HR_MANAGER", "hr"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(SETUP_FLOW_REQUIRED_MESSAGE);

        assertThat(employee.getRole()).isEqualTo(TypeRole.EMPLOYEE);
        verify(teamRepository, never()).existsByTeamLeader(employee);
        verify(keycloakAdminService, never()).assignRoleToUser(anyString(), anyString());
        verify(userService, never()).saveUser(any());
    }

    private void assertLegacyRoleChangeRejected(TypeRole newRole) {
        assertThatThrownBy(() -> service.assignRoleToUser(3L, newRole.name(), "hr"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(SETUP_FLOW_REQUIRED_MESSAGE);

        verify(userService, never()).findById(anyLong());
        verify(keycloakAdminService, never()).assignRoleToUser(anyString(), anyString());
        verify(userService, never()).saveUser(any());
    }

    private void assertLeadingTeamLeaderDemotionRejected(TypeRole newRole) {
        User leader = user(3L, "leader", TypeRole.TEAM_LEADER, true);
        Team team = new Team();
        team.setId(11L);
        team.setTeamLeader(leader);

        when(userService.findById(3L)).thenReturn(Optional.of(leader));
        when(teamRepository.existsByTeamLeader(leader)).thenReturn(true);

        assertThatThrownBy(() -> service.assignRoleToUser(3L, newRole.name(), "hr"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("This Team Leader currently leads a team. Reassign or remove team leadership before changing their role.");

        verify(keycloakAdminService, never()).assignRoleToUser(anyString(), anyString());
        verify(userService, never()).saveUser(any());
    }

    private User user(Long id, String username, TypeRole role, boolean active) {
        User user = new User("kc-" + username, username);
        user.setId(id);
        user.setRole(role);
        user.setActive(active);
        return user;
    }
}
