package tn.isetbizerte.pfe.hrbackend.modules.team.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tn.isetbizerte.pfe.hrbackend.common.event.NotificationEvent;
import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;
import tn.isetbizerte.pfe.hrbackend.common.exception.BadRequestException;
import tn.isetbizerte.pfe.hrbackend.infrastructure.email.HREmailService;
import tn.isetbizerte.pfe.hrbackend.infrastructure.kafka.producer.NotificationEventProducer;
import tn.isetbizerte.pfe.hrbackend.modules.employee.repository.LeaveRequestRepository;
import tn.isetbizerte.pfe.hrbackend.modules.team.entity.Team;
import tn.isetbizerte.pfe.hrbackend.modules.team.repository.TeamRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.Person;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;

import java.util.Map;
import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TeamServiceTest {

    private TeamRepository teamRepository;
    private UserRepository userRepository;
    private LeaveRequestRepository leaveRequestRepository;
    private NotificationEventProducer notificationEventProducer;
    private HREmailService emailService;
    private TeamService teamService;

    @BeforeEach
    void setUp() {
        teamRepository = mock(TeamRepository.class);
        userRepository = mock(UserRepository.class);
        leaveRequestRepository = mock(LeaveRequestRepository.class);
        notificationEventProducer = mock(NotificationEventProducer.class);
        emailService = mock(HREmailService.class);
        teamService = new TeamService(teamRepository, userRepository, leaveRequestRepository,
                notificationEventProducer, emailService);
    }

    @Test
    void createTeam_rejectsDuplicateNameWithSameCasing() {
        when(teamRepository.existsByNameIgnoreCase("Dream Team")).thenReturn(true);

        assertThatThrownBy(() -> teamService.createTeam("Dream Team", "Core team", 2L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("A team with this name already exists.");

        verify(userRepository, never()).findById(any());
        verify(teamRepository, never()).save(any());
    }

    @Test
    void createTeam_rejectsDuplicateNameWithDifferentCasing() {
        when(teamRepository.existsByNameIgnoreCase("dream team")).thenReturn(true);

        assertThatThrownBy(() -> teamService.createTeam("dream team", null, 2L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("A team with this name already exists.");
    }

    @Test
    void createTeam_trimsNameBeforeDuplicateCheckAndSave() {
        User leader = teamLeader(2L, "leader");
        when(teamRepository.existsByNameIgnoreCase("Dream Team")).thenReturn(false);
        when(userRepository.findById(2L)).thenReturn(Optional.of(leader));
        when(teamRepository.findByTeamLeader(leader)).thenReturn(Optional.empty());
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> {
            Team team = invocation.getArgument(0);
            team.setId(7L);
            return team;
        });

        Map<String, Object> response = teamService.createTeam("  Dream Team  ", "  Product delivery  ", 2L);

        assertThat(response.get("name")).isEqualTo("Dream Team");
        assertThat(response.get("description")).isEqualTo("Product delivery");
        assertThat(response.get("employeeMemberCount")).isEqualTo(0);
        assertThat(response.get("teamLeaderCount")).isEqualTo(1);
        assertThat(response.get("totalPeopleCount")).isEqualTo(1);
        verify(teamRepository).existsByNameIgnoreCase("Dream Team");
    }

    @Test
    void createTeam_uniqueTeamSucceeds() {
        User leader = teamLeader(3L, "lead-three");
        when(teamRepository.existsByNameIgnoreCase("Ops")).thenReturn(false);
        when(userRepository.findById(3L)).thenReturn(Optional.of(leader));
        when(teamRepository.findByTeamLeader(leader)).thenReturn(Optional.empty());
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> {
            Team team = invocation.getArgument(0);
            team.setId(8L);
            return team;
        });

        Map<String, Object> response = teamService.createTeam("Ops", null, 3L);

        assertThat(response.get("id")).isEqualTo(8L);
        assertThat(response.get("name")).isEqualTo("Ops");
    }

    @Test
    void changeTeamLeader_replacesLeaderSuccessfullyAndNotifiesBothLeaders() {
        User oldLeader = teamLeader(2L, "old-lead");
        User newLeader = teamLeader(3L, "new-lead");
        Team team = team(20L, "Dream Team", oldLeader);
        User member = employee(10L, "employee");
        member.setTeam(team);
        team.setMembers(List.of(member));
        Team oldLeaderRegularTeam = team(99L, "Old Regular Team", teamLeader(9L, "other-lead"));
        oldLeader.setTeam(oldLeaderRegularTeam);

        when(teamRepository.findByIdWithDetails(20L)).thenReturn(Optional.of(team));
        when(teamRepository.findByIdWithMembers(20L)).thenReturn(Optional.of(team));
        when(userRepository.findById(3L)).thenReturn(Optional.of(newLeader));
        when(teamRepository.findByTeamLeader(newLeader)).thenReturn(Optional.empty());

        Map<String, Object> response = teamService.changeTeamLeader(20L, 3L);

        assertThat(team.getTeamLeader()).isEqualTo(newLeader);
        assertThat(response.get("teamLeaderCount")).isEqualTo(1);
        assertThat(response.get("employeeMemberCount")).isEqualTo(1);
        assertThat(oldLeader.getRole()).isEqualTo(TypeRole.TEAM_LEADER);
        assertThat(oldLeader.getTeam()).isEqualTo(oldLeaderRegularTeam);
        assertThat(newLeader.getTeam()).isNull();
        assertThat(team.getMembers()).containsExactly(member);
        verify(teamRepository).save(team);
        verify(notificationEventProducer).publish(argThat(event ->
                "TEAM_LEADER_ASSIGNED".equals(event.getType())
                        && "kc-3".equals(event.getUserId())
                        && "TEAM".equals(event.getReferenceType())
                        && Long.valueOf(20L).equals(event.getReferenceId())
                        && event.getMessage().equals("You have been assigned as Team Leader of Dream Team.")));
        verify(notificationEventProducer).publish(argThat(event ->
                "TEAM_LEADER_REMOVED".equals(event.getType())
                        && "kc-2".equals(event.getUserId())
                        && Long.valueOf(20L).equals(event.getReferenceId())
                        && event.getMessage().equals("You are no longer Team Leader of Dream Team.")));
        verify(emailService).sendTeamLeaderAssigned("new-lead@example.com", "First3", "Last3", "Dream Team");
        verify(emailService).sendTeamLeaderRemoved("old-lead@example.com", "First2", "Last2", "Dream Team");
    }

    @Test
    void changeTeamLeader_sameLeaderIsNoOpWithoutDuplicateNotifications() {
        User currentLeader = teamLeader(2L, "lead");
        Team team = team(20L, "Dream Team", currentLeader);
        when(teamRepository.findByIdWithDetails(20L)).thenReturn(Optional.of(team));
        when(teamRepository.findByIdWithMembers(20L)).thenReturn(Optional.of(team));
        when(userRepository.findById(2L)).thenReturn(Optional.of(currentLeader));

        Map<String, Object> response = teamService.changeTeamLeader(20L, 2L);

        assertThat(team.getTeamLeader()).isEqualTo(currentLeader);
        assertThat(response.get("name")).isEqualTo("Dream Team");
        verify(teamRepository, never()).save(any());
        verify(notificationEventProducer, never()).publish(any());
        verify(emailService, never()).sendTeamLeaderAssigned(any(), any(), any(), any());
        verify(emailService, never()).sendTeamLeaderRemoved(any(), any(), any(), any());
    }

    @Test
    void changeTeamLeader_inactiveLeaderRejects() {
        User oldLeader = teamLeader(2L, "old-lead");
        User newLeader = teamLeader(3L, "new-lead");
        newLeader.setActive(false);
        Team team = team(20L, "Dream Team", oldLeader);
        when(teamRepository.findByIdWithDetails(20L)).thenReturn(Optional.of(team));
        when(teamRepository.findByIdWithMembers(20L)).thenReturn(Optional.of(team));
        when(userRepository.findById(3L)).thenReturn(Optional.of(newLeader));

        assertThatThrownBy(() -> teamService.changeTeamLeader(20L, 3L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("New Team Leader must be active.");

        assertThat(team.getTeamLeader()).isEqualTo(oldLeader);
        verify(teamRepository, never()).save(any());
    }

    @Test
    void changeTeamLeader_nonTeamLeaderRejects() {
        User oldLeader = teamLeader(2L, "old-lead");
        User newLeader = employee(3L, "employee");
        Team team = team(20L, "Dream Team", oldLeader);
        when(teamRepository.findByIdWithDetails(20L)).thenReturn(Optional.of(team));
        when(teamRepository.findByIdWithMembers(20L)).thenReturn(Optional.of(team));
        when(userRepository.findById(3L)).thenReturn(Optional.of(newLeader));

        assertThatThrownBy(() -> teamService.changeTeamLeader(20L, 3L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("User is not a TEAM_LEADER. Assign the TEAM_LEADER role first.");

        assertThat(team.getTeamLeader()).isEqualTo(oldLeader);
        verify(teamRepository, never()).save(any());
    }

    @Test
    void changeTeamLeader_leaderAlreadyLeadingAnotherTeamRejects() {
        User oldLeader = teamLeader(2L, "old-lead");
        User newLeader = teamLeader(3L, "new-lead");
        Team team = team(20L, "Dream Team", oldLeader);
        Team otherTeam = team(21L, "Other Team", newLeader);
        when(teamRepository.findByIdWithDetails(20L)).thenReturn(Optional.of(team));
        when(teamRepository.findByIdWithMembers(20L)).thenReturn(Optional.of(team));
        when(userRepository.findById(3L)).thenReturn(Optional.of(newLeader));
        when(teamRepository.findByTeamLeader(newLeader)).thenReturn(Optional.of(otherTeam));

        assertThatThrownBy(() -> teamService.changeTeamLeader(20L, 3L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("This Team Leader is already assigned to another team.");

        assertThat(team.getTeamLeader()).isEqualTo(oldLeader);
        verify(teamRepository, never()).save(any());
    }

    @Test
    void changeTeamLeader_leaderWhoIsRegularMemberOfTeamRejects() {
        User oldLeader = teamLeader(2L, "old-lead");
        User newLeader = teamLeader(3L, "new-lead");
        newLeader.setTeam(team(21L, "Member Team", teamLeader(4L, "member-team-lead")));
        Team team = team(20L, "Dream Team", oldLeader);
        when(teamRepository.findByIdWithDetails(20L)).thenReturn(Optional.of(team));
        when(teamRepository.findByIdWithMembers(20L)).thenReturn(Optional.of(team));
        when(userRepository.findById(3L)).thenReturn(Optional.of(newLeader));
        when(teamRepository.findByTeamLeader(newLeader)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.changeTeamLeader(20L, 3L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("This Team Leader is already a regular member of another team.");

        assertThat(team.getTeamLeader()).isEqualTo(oldLeader);
        verify(teamRepository, never()).save(any());
    }

    @Test
    void createTeam_rejectsBlankName() {
        assertThatThrownBy(() -> teamService.createTeam("   ", null, 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Team name is required");

        verify(teamRepository, never()).existsByNameIgnoreCase(any());
    }

    @Test
    void assignEmployeeToTeam_activeEmployeeWithoutTeamSucceedsAndNotifies() {
        User employee = employee(10L, "employee");
        Team target = team(20L, "Dream Team", teamLeader(2L, "leader"));
        when(userRepository.findById(10L)).thenReturn(Optional.of(employee));
        when(teamRepository.findByIdWithDetails(20L)).thenReturn(Optional.of(target));

        Map<String, Object> response = teamService.assignEmployeeToTeam(10L, 20L);

        assertThat(employee.getTeam()).isEqualTo(target);
        assertThat(response.get("teamName")).isEqualTo("Dream Team");
        verify(userRepository).save(employee);
        verify(notificationEventProducer).publish(argThat(event ->
                "TEAM_ASSIGNED".equals(event.getType())
                        && "TEAM".equals(event.getReferenceType())
                        && Long.valueOf(20L).equals(event.getReferenceId())
                        && event.getMessage().contains("Dream Team")));
        verify(emailService).sendTeamAssigned("employee@example.com", "First10", "Last10", "Dream Team");
    }

    @Test
    void assignEmployeeToTeam_teamWithoutLeaderRejects() {
        User employee = employee(10L, "employee");
        Team target = team(20L, "Leaderless", null);
        when(userRepository.findById(10L)).thenReturn(Optional.of(employee));
        when(teamRepository.findByIdWithDetails(20L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> teamService.assignEmployeeToTeam(10L, 20L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Target team must have a Team Leader before assigning members.");

        verify(userRepository, never()).save(any());
        verify(notificationEventProducer, never()).publish(any());
    }

    @Test
    void assignEmployeeToTeam_nonEmployeeRolesReject() {
        for (TypeRole role : new TypeRole[]{TypeRole.HR_MANAGER, TypeRole.NEW_USER, TypeRole.TEAM_LEADER}) {
            User user = employee(10L, "user-" + role.name());
            user.setRole(role);
            when(userRepository.findById(10L)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> teamService.assignEmployeeToTeam(10L, 20L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Only active employees can be assigned to a team.");
        }
    }

    @Test
    void assignEmployeeToTeam_inactiveEmployeeRejects() {
        User employee = employee(10L, "employee");
        employee.setActive(false);
        when(userRepository.findById(10L)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> teamService.assignEmployeeToTeam(10L, 20L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Only active employees can be assigned to a team.");
    }

    @Test
    void assignEmployeeToTeam_employeeAlreadyInTeamRejects() {
        User employee = employee(10L, "employee");
        employee.setTeam(team(11L, "Current", teamLeader(3L, "current-lead")));
        when(userRepository.findById(10L)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> teamService.assignEmployeeToTeam(10L, 20L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("This employee is already assigned to a team.");

        verify(teamRepository, never()).findByIdWithDetails(any());
    }

    @Test
    void moveEmployeeToTeam_differentTeamWithLeaderSucceedsAndNotifies() {
        Team current = team(11L, "Current", teamLeader(3L, "current-lead"));
        Team target = team(20L, "New Team", teamLeader(4L, "new-lead"));
        User employee = employee(10L, "employee");
        employee.setTeam(current);
        when(userRepository.findById(10L)).thenReturn(Optional.of(employee));
        when(teamRepository.findByIdWithDetails(20L)).thenReturn(Optional.of(target));
        when(leaveRequestRepository.existsPendingTeamLeaderApprovalByUserId(10L)).thenReturn(false);

        Map<String, Object> response = teamService.moveEmployeeToTeam(10L, 20L);

        assertThat(employee.getTeam()).isEqualTo(target);
        assertThat(response.get("previousTeamName")).isEqualTo("Current");
        verify(notificationEventProducer).publish(argThat(event ->
                "TEAM_CHANGED".equals(event.getType())
                        && event.getMessage().contains("Current")
                        && event.getMessage().contains("New Team")));
        verify(emailService).sendTeamChanged("employee@example.com", "First10", "Last10", "Current", "New Team");
    }

    @Test
    void moveEmployeeToTeam_sameTeamRejects() {
        Team current = team(20L, "Current", teamLeader(3L, "lead"));
        User employee = employee(10L, "employee");
        employee.setTeam(current);
        when(userRepository.findById(10L)).thenReturn(Optional.of(employee));
        when(teamRepository.findByIdWithDetails(20L)).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> teamService.moveEmployeeToTeam(10L, 20L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Employee is already assigned to this team.");
    }

    @Test
    void moveEmployeeToTeam_pendingTeamLeaderLeaveRejects() {
        Team current = team(11L, "Current", teamLeader(3L, "current-lead"));
        Team target = team(20L, "New Team", teamLeader(4L, "new-lead"));
        User employee = employee(10L, "employee");
        employee.setTeam(current);
        when(userRepository.findById(10L)).thenReturn(Optional.of(employee));
        when(teamRepository.findByIdWithDetails(20L)).thenReturn(Optional.of(target));
        when(leaveRequestRepository.existsPendingTeamLeaderApprovalByUserId(10L)).thenReturn(true);

        assertThatThrownBy(() -> teamService.moveEmployeeToTeam(10L, 20L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Employee has a leave request pending Team Leader approval. Resolve it before changing team assignment.");

        assertThat(employee.getTeam()).isEqualTo(current);
    }

    @Test
    void removeEmployeeFromTeam_succeedsAndNotifies() {
        Team current = team(11L, "Current", teamLeader(3L, "current-lead"));
        User employee = employee(10L, "employee");
        employee.setTeam(current);
        when(userRepository.findById(10L)).thenReturn(Optional.of(employee));
        when(leaveRequestRepository.existsPendingTeamLeaderApprovalByUserId(10L)).thenReturn(false);

        Map<String, Object> response = teamService.removeEmployeeFromTeam(10L);

        assertThat(employee.getTeam()).isNull();
        assertThat(response.get("previousTeamName")).isEqualTo("Current");
        verify(notificationEventProducer).publish(argThat(event ->
                "TEAM_REMOVED".equals(event.getType())
                        && Long.valueOf(11L).equals(event.getReferenceId())
                        && event.getMessage().contains("Current")));
        verify(emailService).sendTeamRemoved("employee@example.com", "First10", "Last10", "Current");
    }

    @Test
    void removeEmployeeFromTeam_pendingTeamLeaderLeaveRejects() {
        Team current = team(11L, "Current", teamLeader(3L, "current-lead"));
        User employee = employee(10L, "employee");
        employee.setTeam(current);
        when(userRepository.findById(10L)).thenReturn(Optional.of(employee));
        when(leaveRequestRepository.existsPendingTeamLeaderApprovalByUserId(10L)).thenReturn(true);

        assertThatThrownBy(() -> teamService.removeEmployeeFromTeam(10L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Employee has a leave request pending Team Leader approval. Resolve it before changing team assignment.");

        assertThat(employee.getTeam()).isEqualTo(current);
    }

    @Test
    void removeEmployeeFromTeam_employeeWithoutTeamRejects() {
        User employee = employee(10L, "employee");
        when(userRepository.findById(10L)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> teamService.removeEmployeeFromTeam(10L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("This employee is not assigned to a team.");
    }

    private User teamLeader(Long id, String username) {
        User user = new User("kc-" + id, username);
        user.setId(id);
        user.setRole(TypeRole.TEAM_LEADER);
        user.setActive(true);
        Person person = new Person("Last" + id, "First" + id, username + "@example.com");
        user.setPerson(person);
        return user;
    }

    private User employee(Long id, String username) {
        User user = new User("kc-" + id, username);
        user.setId(id);
        user.setRole(TypeRole.EMPLOYEE);
        user.setActive(true);
        Person person = new Person("Last" + id, "First" + id, username + "@example.com");
        user.setPerson(person);
        return user;
    }

    private Team team(Long id, String name, User leader) {
        Team team = new Team();
        team.setId(id);
        team.setName(name);
        team.setTeamLeader(leader);
        return team;
    }
}
