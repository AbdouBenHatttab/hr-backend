package tn.isetbizerte.pfe.hrbackend.modules.user.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import tn.isetbizerte.pfe.hrbackend.common.exception.BadRequestException;
import tn.isetbizerte.pfe.hrbackend.modules.department.entity.Department;
import tn.isetbizerte.pfe.hrbackend.modules.department.service.DepartmentService;
import tn.isetbizerte.pfe.hrbackend.modules.jobtitle.entity.JobTitle;
import tn.isetbizerte.pfe.hrbackend.modules.jobtitle.service.JobTitleService;
import tn.isetbizerte.pfe.hrbackend.modules.user.dto.UpdateEmploymentRequest;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.Person;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.PersonRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.service.AuthenticatedUserResolver;
import tn.isetbizerte.pfe.hrbackend.modules.user.service.EmploymentSalaryService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class UserRoleInfoControllerTest {

    private UserRepository userRepository;
    private PersonRepository personRepository;
    private DepartmentService departmentService;
    private JobTitleService jobTitleService;
    private EmploymentSalaryService employmentSalaryService;
    private AuthenticatedUserResolver authenticatedUserResolver;
    private UserRoleInfoController controller;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        personRepository = mock(PersonRepository.class);
        departmentService = mock(DepartmentService.class);
        jobTitleService = mock(JobTitleService.class);
        employmentSalaryService = mock(EmploymentSalaryService.class);
        authenticatedUserResolver = mock(AuthenticatedUserResolver.class);
        controller = new UserRoleInfoController(
                userRepository,
                personRepository,
                departmentService,
                jobTitleService,
                employmentSalaryService,
                authenticatedUserResolver
        );
    }

    @Test
    void updateMyProfile_rejectsDepartmentChanges() {
        User user = userWithPerson("alice");
        when(authenticatedUserResolver.resolve(any())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> controller.updateMyProfile(Map.of("departmentId", 3), jwt("alice")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Department is HR-managed employment data and cannot be changed from self-service.");

        verify(personRepository, never()).save(any());
    }

    @Test
    void getMyProfile_resolvesUserByKeycloakIdWhenUsernameCaseDiffers() {
        User user = userWithPerson("alice");
        when(authenticatedUserResolver.resolve(any())).thenReturn(Optional.of(user));

        Map<String, Object> response = controller.getMyProfile(jwt("ALICE"));

        assertThat(response).containsEntry("success", true);
        assertThat(response.get("username")).isEqualTo("alice");
        verify(authenticatedUserResolver).resolve(any());
    }

    @Test
    void updateEmployment_assignsRealDepartmentById() {
        User user = userWithPerson("bob");
        Department department = new Department();
        department.setId(5L);
        department.setName("Engineering");
        department.setActive(true);
        JobTitle jobTitle = new JobTitle();
        jobTitle.setId(8L);
        jobTitle.setName("Software Engineer");
        jobTitle.setActive(true);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(departmentService.getDepartmentEntity(5L)).thenReturn(department);
        when(jobTitleService.getJobTitleEntity(8L)).thenReturn(jobTitle);
        when(employmentSalaryService.resolveEffectiveSalary(user.getRole())).thenReturn(new BigDecimal("2500"));

        UpdateEmploymentRequest request = new UpdateEmploymentRequest();
        request.setHireDate(LocalDate.of(2026, 4, 1));
        request.setDepartmentId(5L);
        request.setJobTitleId(8L);

        Map<String, Object> response = controller.updateEmployment(1L, request);

        assertThat(response).containsEntry("success", true);
        assertThat(user.getPerson().getDepartment()).isEqualTo("Engineering");
        assertThat(user.getPerson().getDepartmentId()).isEqualTo(5L);
        assertThat(user.getPerson().getJobTitle()).isEqualTo("Software Engineer");
        assertThat(user.getPerson().getJobTitleId()).isEqualTo(8L);
        assertThat(user.getPerson().getSalary()).isEqualTo(new BigDecimal("2500"));
        verify(employmentSalaryService).resolveEffectiveSalary(user.getRole());
        verify(personRepository).save(user.getPerson());
    }

    private User userWithPerson(String username) {
        Person person = new Person();
        person.setFirstName("Test");
        person.setLastName("User");
        person.setEmail(username + "@example.com");

        User user = new User("kc-" + username, username);
        user.setId(1L);
        user.setPerson(person);
        person.setUser(user);
        return user;
    }

    private Jwt jwt(String username) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "kc-" + username.toLowerCase())
                .claim("preferred_username", username)
                .build();
    }
}
