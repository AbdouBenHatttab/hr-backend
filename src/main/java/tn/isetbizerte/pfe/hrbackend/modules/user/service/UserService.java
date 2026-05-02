package tn.isetbizerte.pfe.hrbackend.modules.user.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.isetbizerte.pfe.hrbackend.common.dto.RegisterRequest;
import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;
import tn.isetbizerte.pfe.hrbackend.modules.department.service.DepartmentService;
import tn.isetbizerte.pfe.hrbackend.common.exception.ResourceNotFoundException;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.Person;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.LoginHistory;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.PersonRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.LoginHistoryRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Service layer for User operations
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PersonRepository personRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final DepartmentService departmentService;
    private final EmploymentSalaryService employmentSalaryService;

    public UserService(UserRepository userRepository,
                       PersonRepository personRepository,
                       LoginHistoryRepository loginHistoryRepository,
                       DepartmentService departmentService,
                       EmploymentSalaryService employmentSalaryService) {
        this.userRepository = userRepository;
        this.personRepository = personRepository;
        this.loginHistoryRepository = loginHistoryRepository;
        this.departmentService = departmentService;
        this.employmentSalaryService = employmentSalaryService;
    }

    // User operations
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public Page<User> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Optional<User> findByUsernameIgnoreCaseWithPerson(String username) {
        return userRepository.findByUsernameIgnoreCaseWithPerson(username);
    }

    public Optional<User> findByKeycloakId(String keycloakId) {
        return userRepository.findByKeycloakId(keycloakId);
    }

    public Optional<User> findByPersonEmailIgnoreCaseWithPerson(String email) {
        return userRepository.findByPersonEmailIgnoreCaseWithPerson(email);
    }

    public Optional<User> findById(Long userId) {
        return userRepository.findById(userId);
    }

    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
    }

    public List<User> getUsersByRole(TypeRole role) {
        return userRepository.findByRole(role);
    }

    public Page<User> getUsersByRole(TypeRole role, Pageable pageable) {
        return userRepository.findByRole(role, pageable);
    }

    public List<User> getActiveUsers() {
        return userRepository.findByActive(true);
    }

    public User saveUser(User user) {
        return userRepository.save(user);
    }

    public User updateUserRole(String username, TypeRole newRole) {
        User user = getUserByUsername(username);
        user.setRole(newRole);
        User saved = userRepository.save(user);
        syncRoleBasedSalary(saved);
        return saved;
    }

    /**
     * Update user role by username using role name from Keycloak
     * Converts string role name to TypeRole enum
     */
    public User updateUserRoleByUsername(String username, String roleFromKeycloak) {
        try {
            User user = getUserByUsername(username);

            // Convert Keycloak role string to TypeRole enum
            TypeRole typeRole = TypeRole.valueOf(roleFromKeycloak);
            user.setRole(typeRole);
            User saved = userRepository.save(user);

            // Keep salary system-controlled and role-based.
            if (typeRole != TypeRole.NEW_USER && saved.getPerson() != null) {
                Person p = saved.getPerson();
                if (p.getHireDate() == null) {
                    p.setHireDate(LocalDate.now());
                }
                p.setSalary(employmentSalaryService.resolveEffectiveSalary(typeRole));
                personRepository.save(p);
            }

            return saved;
        } catch (IllegalArgumentException e) {
            // Invalid role name - silently ignore, this is non-critical
            // The JWT token has the correct role anyway
            return null;
        } catch (ResourceNotFoundException e) {
            // User not found - this shouldn't happen for valid users
            return null;
        }
    }

    public long countUsers() {
        return userRepository.count();
    }

    public long countUsersByRole(TypeRole role) {
        return userRepository.countByRole(role);
    }

    // Person operations
    public List<Person> getAllPersons() {
        return personRepository.findAll();
    }

    public Person savePerson(Person person) {
        return personRepository.save(person);
    }

    public void syncRoleBasedSalary(User user) {
        if (user == null || user.getPerson() == null) {
            return;
        }
        Person person = user.getPerson();
        person.setSalary(employmentSalaryService.resolveEffectiveSalary(user.getRole()));
        personRepository.save(person);
    }

    @Transactional
    public void saveRegisteredUser(RegisterRequest registerRequest, String keycloakUserId) {
        Person person = new Person();
        person.setFirstName(registerRequest.getFirstName());
        person.setLastName(registerRequest.getLastName());
        person.setEmail(registerRequest.getEmail());
        person.setPhone(registerRequest.getPhone());
        person.setBirthDate(registerRequest.getBirthDate());
        person.setAddress(registerRequest.getAddress());
        person.setMaritalStatus(registerRequest.getMaritalStatus());
        person.setNumberOfChildren(registerRequest.getNumberOfChildren());

        User user = new User(keycloakUserId, registerRequest.getUsername());
        user.setEmailVerified(true);
        user.setActive(false);
        user.setPerson(person);
        person.setUser(user);

        personRepository.save(person);
        userRepository.save(user);
    }

    public long countPersons() {
        return personRepository.count();
    }

    // Login history operations
    public List<LoginHistory> getAllLoginHistory() {
        return loginHistoryRepository.findAll();
    }

    public Page<LoginHistory> getAllLoginHistory(Pageable pageable) {
        return loginHistoryRepository.findAll(pageable);
    }

    public List<LoginHistory> getLoginHistoryByUser(User user) {
        return loginHistoryRepository.findByUserOrderByLoginDateDesc(user);
    }

    public void saveLoginHistory(String username, String ipAddress, String browser, boolean success) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isPresent()) {
            LoginHistory loginHistory = new LoginHistory(userOpt.get(), success);
            loginHistoryRepository.save(loginHistory);
        }
    }

    public long countLoginHistory() {
        return loginHistoryRepository.count();
    }
}
