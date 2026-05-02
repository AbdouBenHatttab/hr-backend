package tn.isetbizerte.pfe.hrbackend.modules.hr.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tn.isetbizerte.pfe.hrbackend.common.enums.DocumentType;
import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;
import tn.isetbizerte.pfe.hrbackend.infrastructure.kafka.producer.KafkaEventProducer;
import tn.isetbizerte.pfe.hrbackend.modules.department.entity.Department;
import tn.isetbizerte.pfe.hrbackend.modules.department.service.DepartmentService;
import tn.isetbizerte.pfe.hrbackend.modules.jobtitle.entity.JobTitle;
import tn.isetbizerte.pfe.hrbackend.modules.jobtitle.service.JobTitleService;
import tn.isetbizerte.pfe.hrbackend.modules.requests.entity.StoredEmployeeDocument;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.StoredEmployeeDocumentRepository;
import tn.isetbizerte.pfe.hrbackend.modules.team.entity.Team;
import tn.isetbizerte.pfe.hrbackend.modules.team.repository.TeamRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.Person;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.service.EmploymentSalaryService;
import tn.isetbizerte.pfe.hrbackend.modules.user.service.UserService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HRServiceUserReadinessTest {

    private UserService userService;
    private StoredEmployeeDocumentRepository storedDocumentRepository;
    private TeamRepository teamRepository;
    private HRService service;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        storedDocumentRepository = mock(StoredEmployeeDocumentRepository.class);
        teamRepository = mock(TeamRepository.class);
        service = new HRService(
                userService,
                mock(KeycloakAdminService.class),
                mock(KafkaEventProducer.class),
                storedDocumentRepository,
                teamRepository,
                mock(DepartmentService.class),
                mock(JobTitleService.class),
                mock(EmploymentSalaryService.class)
        );
    }

    @Test
    void newUser_returnsPendingRoleAndNoSetupIssues() {
        User user = user(1L, "newbie", TypeRole.NEW_USER);
        when(userService.getAllUsers()).thenReturn(List.of(user));

        Map<String, Object> response = firstUser();

        assertThat(response).containsEntry("setupStatus", "PENDING_ROLE");
        assertThat(response.get("setupIssues")).isEqualTo(List.of());
        assertThat(response).containsKeys("id", "username", "role", "personalInfo", "requiredDocuments");
    }

    @Test
    void employeeMissingAllSetup_returnsRoleAwareIssues() {
        User user = user(2L, "employee", TypeRole.EMPLOYEE);
        when(userService.getAllUsers()).thenReturn(List.of(user));
        when(storedDocumentRepository.findFirstByEmployeeAndDocumentTypeAndActiveTrueOrderByUploadedAtDesc(
                user, DocumentType.CONTRACT_COPY)).thenReturn(Optional.empty());

        Map<String, Object> response = firstUser();

        assertThat(response).containsEntry("setupStatus", "INCOMPLETE");
        assertThat(response.get("setupIssues")).isEqualTo(List.of(
                "MISSING_DEPARTMENT",
                "MISSING_JOB_TITLE",
                "MISSING_HIRE_DATE",
                "MISSING_TEAM",
                "MISSING_CONTRACT_COPY"
        ));
        assertThat(response.get("teamInfo")).isNull();
    }

    @Test
    void employeeCompleteSetup_returnsComplete() {
        User leader = user(9L, "leader", TypeRole.TEAM_LEADER);
        completeEmployment(leader);
        User employee = user(3L, "employee", TypeRole.EMPLOYEE);
        completeEmployment(employee);
        Team team = team(20L, "Delivery", leader);
        employee.setTeam(team);
        StoredEmployeeDocument document = contractCopy(33L);

        when(userService.getAllUsers()).thenReturn(List.of(employee));
        when(storedDocumentRepository.findFirstByEmployeeAndDocumentTypeAndActiveTrueOrderByUploadedAtDesc(
                employee, DocumentType.CONTRACT_COPY)).thenReturn(Optional.of(document));

        Map<String, Object> response = firstUser();

        assertThat(response).containsEntry("setupStatus", "COMPLETE");
        assertThat(response.get("setupIssues")).isEqualTo(List.of());
        assertThat((Map<String, Object>) response.get("teamInfo"))
                .containsEntry("teamId", 20L)
                .containsEntry("teamName", "Delivery")
                .containsEntry("teamRole", "Member")
                .containsEntry("teamLeaderId", 9L)
                .containsEntry("teamLeaderName", "Leader User");
    }

    @Test
    void teamLeaderWithoutLedTeam_returnsMissingLedTeam() {
        User leader = user(4L, "leader", TypeRole.TEAM_LEADER);
        completeEmployment(leader);
        when(userService.getAllUsers()).thenReturn(List.of(leader));
        when(teamRepository.findByTeamLeader(leader)).thenReturn(Optional.empty());
        when(storedDocumentRepository.findFirstByEmployeeAndDocumentTypeAndActiveTrueOrderByUploadedAtDesc(
                leader, DocumentType.CONTRACT_COPY)).thenReturn(Optional.of(contractCopy(44L)));

        Map<String, Object> response = firstUser();

        assertThat(response).containsEntry("setupStatus", "INCOMPLETE");
        assertThat(response.get("setupIssues")).isEqualTo(List.of("MISSING_LED_TEAM"));
        assertThat(response.get("teamInfo")).isNull();
    }

    @Test
    void teamLeaderWithLedTeam_returnsLeaderTeamInfo() {
        User leader = user(5L, "leader", TypeRole.TEAM_LEADER);
        completeEmployment(leader);
        Team team = team(50L, "Platform", leader);
        when(userService.getAllUsers()).thenReturn(List.of(leader));
        when(teamRepository.findByTeamLeader(leader)).thenReturn(Optional.of(team));
        when(storedDocumentRepository.findFirstByEmployeeAndDocumentTypeAndActiveTrueOrderByUploadedAtDesc(
                leader, DocumentType.CONTRACT_COPY)).thenReturn(Optional.of(contractCopy(55L)));

        Map<String, Object> response = firstUser();

        assertThat(response).containsEntry("setupStatus", "COMPLETE");
        assertThat(response.get("setupIssues")).isEqualTo(List.of());
        assertThat((Map<String, Object>) response.get("teamInfo"))
                .containsEntry("teamId", 50L)
                .containsEntry("teamName", "Platform")
                .containsEntry("teamRole", "Team Leader")
                .containsEntry("teamLeaderId", 5L)
                .containsEntry("teamLeaderName", "Leader User");
    }

    @Test
    void hrManagerWithoutTeam_isCompleteWhenEmploymentExists() {
        User hr = user(6L, "hr", TypeRole.HR_MANAGER);
        completeEmployment(hr);
        when(userService.getAllUsers()).thenReturn(List.of(hr));

        Map<String, Object> response = firstUser();

        assertThat(response).containsEntry("setupStatus", "COMPLETE");
        assertThat(response.get("setupIssues")).isEqualTo(List.of());
        assertThat(response.get("teamInfo")).isNull();
        assertThat((Map<String, Object>) response.get("requiredDocuments"))
                .extracting("contractCopy")
                .satisfies(contractCopy -> assertThat((Map<String, Object>) contractCopy)
                        .containsEntry("required", false)
                        .containsEntry("uploaded", false));
    }

    @Test
    void hrManagerWithoutTeam_isNotFlaggedForTeamOrContract() {
        User hr = user(7L, "hr", TypeRole.HR_MANAGER);
        when(userService.getAllUsers()).thenReturn(List.of(hr));

        Map<String, Object> response = firstUser();

        assertThat(response).containsEntry("setupStatus", "INCOMPLETE");
        assertThat((List<String>) response.get("setupIssues"))
                .containsExactly("MISSING_DEPARTMENT", "MISSING_JOB_TITLE", "MISSING_HIRE_DATE")
                .doesNotContain("MISSING_TEAM", "MISSING_LED_TEAM", "MISSING_CONTRACT_COPY");
    }

    @Test
    void userResponse_preservesExistingFields() {
        User user = user(8L, "employee", TypeRole.EMPLOYEE);
        completeEmployment(user);
        when(userService.getAllUsers()).thenReturn(List.of(user));
        when(storedDocumentRepository.findFirstByEmployeeAndDocumentTypeAndActiveTrueOrderByUploadedAtDesc(
                user, DocumentType.CONTRACT_COPY)).thenReturn(Optional.empty());

        Map<String, Object> response = firstUser();

        assertThat(response).containsKeys(
                "id",
                "username",
                "keycloakId",
                "registrationDate",
                "active",
                "emailVerified",
                "role",
                "personalInfo",
                "requiredDocuments"
        );
        assertThat((Map<String, Object>) response.get("personalInfo")).containsKeys(
                "firstName",
                "lastName",
                "email",
                "phone",
                "address",
                "birthDate",
                "maritalStatus",
                "numberOfChildren",
                "departmentId",
                "department",
                "jobTitleId",
                "jobTitle",
                "salary",
                "hireDate",
                "avatarPhoto",
                "avatarColor"
        );
    }

    private Map<String, Object> firstUser() {
        return service.getAllUsersWithDetails().get(0);
    }

    private User user(Long id, String username, TypeRole role) {
        User user = new User("kc-" + username, username);
        user.setId(id);
        user.setRole(role);
        user.setActive(true);

        Person person = new Person();
        person.setFirstName(capitalize(username));
        person.setLastName("User");
        person.setEmail(username + "@example.com");
        person.setUser(user);
        user.setPerson(person);
        return user;
    }

    private void completeEmployment(User user) {
        Department department = new Department();
        department.setId(10L);
        department.setName("Engineering");
        department.setActive(true);
        JobTitle jobTitle = new JobTitle();
        jobTitle.setId(11L);
        jobTitle.setName("Engineer");
        jobTitle.setActive(true);
        user.getPerson().setDepartmentRef(department);
        user.getPerson().setJobTitleRef(jobTitle);
        user.getPerson().setHireDate(LocalDate.of(2026, 4, 1));
    }

    private Team team(Long id, String name, User leader) {
        Team team = new Team();
        team.setId(id);
        team.setName(name);
        team.setTeamLeader(leader);
        return team;
    }

    private StoredEmployeeDocument contractCopy(Long id) {
        StoredEmployeeDocument document = new StoredEmployeeDocument();
        document.setId(id);
        document.setDocumentType(DocumentType.CONTRACT_COPY);
        document.setFileName("contract.pdf");
        return document;
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.substring(0, 1).toUpperCase() + value.substring(1);
    }
}
