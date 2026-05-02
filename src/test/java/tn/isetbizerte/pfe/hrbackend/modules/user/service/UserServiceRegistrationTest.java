package tn.isetbizerte.pfe.hrbackend.modules.user.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tn.isetbizerte.pfe.hrbackend.common.dto.RegisterRequest;
import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;
import tn.isetbizerte.pfe.hrbackend.common.exception.ResourceNotFoundException;
import tn.isetbizerte.pfe.hrbackend.modules.department.service.DepartmentService;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.Person;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.LoginHistoryRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.PersonRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceRegistrationTest {

    private UserRepository userRepository;
    private PersonRepository personRepository;
    private DepartmentService departmentService;
    private UserService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        personRepository = mock(PersonRepository.class);
        departmentService = mock(DepartmentService.class);
        service = new UserService(
                userRepository,
                personRepository,
                mock(LoginHistoryRepository.class),
                departmentService,
                mock(EmploymentSalaryService.class)
        );

        when(personRepository.save(any(Person.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void registrationWithoutDepartmentIdCreatesPendingNewUser() {
        RegisterRequest request = validRequest();

        service.saveRegisteredUser(request, "kc-123");

        User savedUser = savedUser();
        Person savedPerson = savedPerson();
        assertThat(savedUser.getRole()).isEqualTo(TypeRole.NEW_USER);
        assertThat(savedUser.getActive()).isFalse();
        assertThat(savedUser.getPerson()).isSameAs(savedPerson);
        assertThat(savedPerson.getUser()).isSameAs(savedUser);
        assertThat(savedPerson.getDepartmentRef()).isNull();
        assertThat(savedPerson.getJobTitleRef()).isNull();
        assertThat(savedPerson.getHireDate()).isNull();
        assertThat(savedUser.getTeam()).isNull();
        verify(departmentService, never()).requireDepartmentForEmployment(any());
    }

    @Test
    void oldClientSendingValidDepartmentIdRegistersButDoesNotAssignDepartment() {
        RegisterRequest request = validRequest();
        request.setDepartmentId(10L);

        service.saveRegisteredUser(request, "kc-456");

        assertThat(savedPerson().getDepartmentRef()).isNull();
        verify(departmentService, never()).requireDepartmentForEmployment(any());
    }

    @Test
    void oldClientSendingInvalidDepartmentIdDoesNotBreakRegistration() {
        RegisterRequest request = validRequest();
        request.setDepartmentId(999L);
        when(departmentService.requireDepartmentForEmployment(999L))
                .thenThrow(new ResourceNotFoundException("Department not found with ID: 999"));

        assertThatNoException().isThrownBy(() -> service.saveRegisteredUser(request, "kc-789"));

        assertThat(savedPerson().getDepartmentRef()).isNull();
        verify(departmentService, never()).requireDepartmentForEmployment(any());
    }

    private RegisterRequest validRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Nadia");
        request.setLastName("Ben Ali");
        request.setUsername("nadia.benali");
        request.setEmail("nadia@example.com");
        request.setPhone("+216 12 345 678");
        request.setBirthDate(LocalDate.of(1995, 2, 20));
        request.setAddress("Bizerte, Tunisia");
        request.setMaritalStatus("Single");
        request.setNumberOfChildren(0);
        return request;
    }

    private User savedUser() {
        var captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        return captor.getValue();
    }

    private Person savedPerson() {
        var captor = org.mockito.ArgumentCaptor.forClass(Person.class);
        verify(personRepository).save(captor.capture());
        return captor.getValue();
    }
}
