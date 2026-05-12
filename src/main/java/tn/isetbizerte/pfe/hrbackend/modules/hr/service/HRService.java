package tn.isetbizerte.pfe.hrbackend.modules.hr.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import tn.isetbizerte.pfe.hrbackend.common.enums.DocumentType;
import org.springframework.stereotype.Service;
import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;
import tn.isetbizerte.pfe.hrbackend.common.event.UserRoleAssignedEvent;
import tn.isetbizerte.pfe.hrbackend.common.exception.BadRequestException;
import tn.isetbizerte.pfe.hrbackend.common.exception.ResourceNotFoundException;
import tn.isetbizerte.pfe.hrbackend.infrastructure.kafka.producer.KafkaEventProducer;
import tn.isetbizerte.pfe.hrbackend.modules.hr.dto.AssignRoleResponse;
import tn.isetbizerte.pfe.hrbackend.modules.hr.dto.CompleteUserSetupRequest;
import tn.isetbizerte.pfe.hrbackend.modules.department.entity.Department;
import tn.isetbizerte.pfe.hrbackend.modules.department.service.DepartmentService;
import tn.isetbizerte.pfe.hrbackend.modules.jobtitle.entity.JobTitle;
import tn.isetbizerte.pfe.hrbackend.modules.jobtitle.service.JobTitleService;
import tn.isetbizerte.pfe.hrbackend.modules.requests.entity.StoredEmployeeDocument;
import tn.isetbizerte.pfe.hrbackend.modules.requests.repository.StoredEmployeeDocumentRepository;
import tn.isetbizerte.pfe.hrbackend.modules.team.entity.Team;
import tn.isetbizerte.pfe.hrbackend.modules.team.repository.TeamRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.Person;
import tn.isetbizerte.pfe.hrbackend.modules.user.service.EmploymentSalaryService;
import tn.isetbizerte.pfe.hrbackend.modules.user.service.UserService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


@Service
public class HRService {

    private static final Logger logger = LoggerFactory.getLogger(HRService.class);
    private static final String SETUP_FLOW_REQUIRED_MESSAGE =
            "Use the HR setup flow to assign or change roles with required employment and team setup.";

    private final UserService userService;
    private final KeycloakAdminService keycloakAdminService;
    private final KafkaEventProducer kafkaEventProducer;
    private final StoredEmployeeDocumentRepository storedDocumentRepository;
    private final TeamRepository teamRepository;
    private final DepartmentService departmentService;
    private final JobTitleService jobTitleService;
    private final EmploymentSalaryService employmentSalaryService;

    public HRService(UserService userService,
                     KeycloakAdminService keycloakAdminService,
                     KafkaEventProducer kafkaEventProducer,
                     StoredEmployeeDocumentRepository storedDocumentRepository,
                     TeamRepository teamRepository,
                     DepartmentService departmentService,
                     JobTitleService jobTitleService,
                     EmploymentSalaryService employmentSalaryService) {
        this.userService = userService;
        this.keycloakAdminService = keycloakAdminService;
        this.kafkaEventProducer = kafkaEventProducer;
        this.storedDocumentRepository = storedDocumentRepository;
        this.teamRepository = teamRepository;
        this.departmentService = departmentService;
        this.jobTitleService = jobTitleService;
        this.employmentSalaryService = employmentSalaryService;
    }

    /**
     * Get dashboard statistics
     */
    public Map<String, Object> getDashboardStatistics() {
        Map<String, Object> statistics = new HashMap<>();

        long totalUsers = userService.countUsers();
        long totalPersons = userService.countPersons();
        long totalLoginAttempts = userService.countLoginHistory();
        long pendingApprovals = userService.countUsersByRole(TypeRole.NEW_USER);

        statistics.put("totalUsers", totalUsers);
        statistics.put("totalPersons", totalPersons);
        statistics.put("totalLoginAttempts", totalLoginAttempts);
        statistics.put("pendingApprovals", pendingApprovals);

        return statistics;
    }

    /**
     * Get all users with their information
     */
    public List<Map<String, Object>> getAllUsersWithDetails() {
        return userService.getAllUsers().stream()
                .map(this::mapUserToDetails)
                .collect(Collectors.toList());
    }

    public Page<Map<String, Object>> getAllUsersWithDetails(Pageable pageable) {
        return userService.getAllUsers(pageable).map(this::mapUserToDetails);
    }

    /**
     * Get users pending approval (NEW_USER role)
     */
    public List<Map<String, Object>> getPendingApprovals() {
        return userService.getUsersByRole(TypeRole.NEW_USER).stream()
                .map(user -> {
                    Map<String, Object> userInfo = new HashMap<>();
                    userInfo.put("id", user.getId());
                    userInfo.put("username", user.getUsername());
                    userInfo.put("registrationDate", user.getRegistrationDate());
                    userInfo.put("role", user.getRole());

                    if (user.getPerson() != null) {
                        Person person = user.getPerson();
                        userInfo.put("fullName", person.getFirstName() + " " + person.getLastName());
                        userInfo.put("email", person.getEmail());
                    }
                    return userInfo;
                })
                .collect(Collectors.toList());
    }

    public Page<Map<String, Object>> getPendingApprovals(Pageable pageable) {
        return userService.getUsersByRole(TypeRole.NEW_USER, pageable).map(user -> {
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", user.getId());
            userInfo.put("username", user.getUsername());
            userInfo.put("registrationDate", user.getRegistrationDate());
            userInfo.put("role", user.getRole());

            if (user.getPerson() != null) {
                Person person = user.getPerson();
                userInfo.put("fullName", person.getFirstName() + " " + person.getLastName());
                userInfo.put("email", person.getEmail());
            }
            return userInfo;
        });
    }

    /**
     * Get all login history (simplified: date/time, id, username only)
     */
    public List<Map<String, Object>> getLoginHistory() {
        return userService.getAllLoginHistory().stream()
                .map(history -> {
                    Map<String, Object> historyInfo = new HashMap<>();
                    historyInfo.put("id", history.getId());
                    historyInfo.put("loginDate", history.getLoginDate());
                    historyInfo.put("userId", history.getUser() != null ? history.getUser().getId() : null);
                    historyInfo.put("username", history.getUser() != null ? history.getUser().getUsername() : "Unknown");
                    return historyInfo;
                })
                .collect(Collectors.toList());
    }

    public Page<Map<String, Object>> getLoginHistory(Pageable pageable) {
        return userService.getAllLoginHistory(pageable).map(history -> {
            Map<String, Object> historyInfo = new HashMap<>();
            historyInfo.put("id", history.getId());
            historyInfo.put("loginDate", history.getLoginDate());
            historyInfo.put("userId", history.getUser() != null ? history.getUser().getId() : null);
            historyInfo.put("username", history.getUser() != null ? history.getUser().getUsername() : "Unknown");
            return historyInfo;
        });
    }

    /**
     * Update user role
     */
    public User updateUserRole(String username, TypeRole newRole) {
        return userService.updateUserRole(username, newRole);
    }

    /**
     * Get user role by username
     */
    public Map<String, Object> getUserRoleInfo(String username) {
        User user = userService.getUserByUsername(username);
        Map<String, Object> info = new HashMap<>();
        info.put("username", user.getUsername());
        info.put("keycloakId", user.getKeycloakId());
        info.put("role", user.getRole() != null ? user.getRole().name() : "null");
        info.put("active", user.getActive());
        info.put("emailVerified", user.getEmailVerified());
        return info;
    }

    /**
     * Find user by ID
     */
    public Optional<User> findUserById(Long userId) {
        return userService.findById(userId);
    }

    /**
     * Update user role by user entity
     */
    public User updateUserRole(User user, TypeRole newRole) {
        user.setRole(newRole);
        return userService.saveUser(user);
    }

    @Transactional
    public Map<String, Object> completeUserSetup(Long userId,
                                                 CompleteUserSetupRequest request,
                                                 String actorKeycloakId,
                                                 String actorUsername) {
        if (request == null) {
            throw new BadRequestException("Setup payload is required.");
        }

        TypeRole newRole = parseSetupRole(request.getRole());
        User user = userService.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        if (user.getPerson() == null) {
            throw new BadRequestException("User has no person profile. Cannot complete HR setup.");
        }
        if (isSameActor(user, actorKeycloakId, actorUsername)) {
            throw new BadRequestException("You cannot change your own role or HR-managed setup.");
        }

        TypeRole oldRole = user.getRole();
        String keycloakUserId = user.getKeycloakId();
        if (keycloakUserId == null || keycloakUserId.isBlank()) {
            throw new BadRequestException("User has no Keycloak ID associated. Cannot update role in Keycloak.");
        }

        Department department = requireSetupDepartment(request.getDepartmentId());
        JobTitle jobTitle = requireSetupJobTitle(request.getJobTitleId());
        if (request.getHireDate() == null) {
            throw new BadRequestException("Hire date is required.");
        }

        // Validate the integrated HR setup before changing Keycloak or local user state.
        validateRoleSpecificSetup(user, newRole, request);
        Team employeeTeam = null;
        Team ledTeam = null;
        if (newRole == TypeRole.EMPLOYEE) {
            employeeTeam = loadTeamRequiredForEmployee(request.getTeamId());
        } else if (newRole == TypeRole.TEAM_LEADER) {
            if (request.getLedTeamId() != null) {
                ledTeam = loadTeamRequiredForLeadership(request.getLedTeamId());
                validateLeadershipTarget(user, ledTeam);
            }
        }

        boolean keycloakUpdateSuccess = keycloakAdminService.assignRoleToUser(keycloakUserId, newRole.name());
        if (!keycloakUpdateSuccess) {
            throw new BadRequestException("Failed to update role in Keycloak. Please check Keycloak connection and logs.");
        }

        boolean pendingApproval = oldRole == TypeRole.NEW_USER && Boolean.FALSE.equals(user.getActive());
        if (pendingApproval) {
            boolean keycloakEnabled = keycloakAdminService.setUserEnabled(keycloakUserId, true);
            if (!keycloakEnabled) {
                keycloakAdminService.assignRoleToUser(keycloakUserId, oldRole.name());
                throw new BadRequestException("Failed to enable user in Keycloak. Local setup was not changed.");
            }
        }

        try {
            user.setRole(newRole);
            if (pendingApproval) {
                user.setActive(true);
            }

            Person person = user.getPerson();
            person.setDepartmentRef(department);
            person.setJobTitleRef(jobTitle);
            person.setHireDate(request.getHireDate());
            person.setSalary(employmentSalaryService.resolveEffectiveSalary(newRole));

            if (newRole == TypeRole.EMPLOYEE) {
                user.setTeam(employeeTeam);
                touchTeam(employeeTeam);
            } else {
                user.setTeam(null);
            }

            userService.saveUser(user);
            userService.savePerson(person);

            if (newRole == TypeRole.TEAM_LEADER && ledTeam != null) {
                ledTeam.setTeamLeader(user);
                touchTeam(ledTeam);
            }

            boolean eventPublished = publishRoleAssignedEvent(user, oldRole, newRole, actorUsername);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "User setup completed successfully.");
            response.put("userId", userId);
            response.put("username", user.getUsername());
            response.put("oldRole", oldRole != null ? oldRole.name() : "NONE");
            response.put("newRole", newRole.name());
            response.put("active", user.getActive());
            response.put("departmentId", department.getId());
            response.put("jobTitleId", jobTitle.getId());
            response.put("hireDate", person.getHireDate());
            response.put("teamId", employeeTeam != null ? employeeTeam.getId() : null);
            response.put("ledTeamId", ledTeam != null ? ledTeam.getId() : null);
            response.put("eventPublished", eventPublished);
            response.put("note", "User must logout and login again to get the new role in their token");
            return response;
        } catch (RuntimeException ex) {
            keycloakAdminService.assignRoleToUser(keycloakUserId, oldRole.name());
            if (pendingApproval) {
                keycloakAdminService.setUserEnabled(keycloakUserId, false);
            }
            throw ex;
        }
    }

    /**
     * Assign role to user by userId - contains all business logic.
     * Flow:
     * 1. Validate user exists
     * 2. Update role in Keycloak
     * 3. Update role in database
     * 4. Publish Kafka event for email notification
     *
     * @param userId The user ID to assign role to
     * @param roleStr The role to assign
     * @param assignedBy The username of the HR manager assigning the role
     * @return AssignRoleResponse with result details
     */
    public AssignRoleResponse assignRoleToUser(Long userId, String roleStr, String assignedBy) {
        logger.info("Assigning role {} to user ID {} by {}", roleStr, userId, assignedBy);

        // Validate role
        TypeRole newRole;
        try {
            newRole = TypeRole.valueOf(roleStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid role. Valid roles are: EMPLOYEE, TEAM_LEADER, HR_MANAGER, NEW_USER");
        }
        // Compatibility-only endpoint: normal active role assignment must go through
        // completeUserSetup so employment and team invariants are validated atomically.
        if (newRole != TypeRole.NEW_USER) {
            throw new BadRequestException(SETUP_FLOW_REQUIRED_MESSAGE);
        }

        // Find user
        User user = userService.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        if (isSameActor(user, null, assignedBy)) {
            throw new BadRequestException("You cannot change your own role.");
        }

        TypeRole oldRole = user.getRole();

        if (oldRole == TypeRole.TEAM_LEADER
                && newRole != TypeRole.TEAM_LEADER
                && teamRepository.existsByTeamLeader(user)) {
            throw new BadRequestException(
                    "This Team Leader currently leads a team. Reassign or remove team leadership before changing their role.");
        }

        // Validate Keycloak ID exists
        String keycloakUserId = user.getKeycloakId();
        if (keycloakUserId == null || keycloakUserId.isEmpty()) {
            throw new BadRequestException("User has no Keycloak ID associated. Cannot update role in Keycloak.");
        }

        // Update role in Keycloak first
        boolean keycloakUpdateSuccess = keycloakAdminService.assignRoleToUser(keycloakUserId, newRole.name());
        if (!keycloakUpdateSuccess) {
            throw new BadRequestException("Failed to update role in Keycloak. Please check Keycloak connection and logs.");
        }

        // Update role in database. Only first approval should activate a pending user;
        // role edits must not silently reactivate a manually deactivated account.
        boolean pendingApproval = oldRole == TypeRole.NEW_USER && Boolean.FALSE.equals(user.getActive());
        user.setRole(newRole);
        if (pendingApproval && newRole != TypeRole.NEW_USER) {
            boolean keycloakEnabled = keycloakAdminService.setUserEnabled(keycloakUserId, true);
            if (!keycloakEnabled) {
                throw new BadRequestException("Failed to enable user in Keycloak. Local role was not changed.");
            }
            user.setActive(true);
        }
        userService.saveUser(user);
        logger.info("Role updated in database for user: {}", user.getUsername());

        // Keep salary system-controlled and role-based.
        if (newRole != TypeRole.NEW_USER && user.getPerson() != null) {
            Person p = user.getPerson();
            if (p.getHireDate() == null) {
                p.setHireDate(LocalDate.now());
            }
            userService.savePerson(p);
        }
        userService.syncRoleBasedSalary(user);

        // Publish Kafka event for email notification
        boolean eventPublished = publishRoleAssignedEvent(user, oldRole, newRole, assignedBy);

        // Build response
        return AssignRoleResponse.builder()
                .success(true)
                .message("Role assigned successfully in both Keycloak and database")
                .userId(userId)
                .username(user.getUsername())
                .oldRole(oldRole != null ? oldRole.name() : "NONE")
                .newRole(newRole.name())
                .assignedBy(assignedBy)
                .timestamp(LocalDateTime.now())
                .eventPublished(eventPublished)
                .note("User must logout and login again to get the new role in their token")
                .build();
    }

    /**
     * Publishes UserRoleAssignedEvent to Kafka for email notification.
     * If Kafka is not enabled, sends email directly.
     */
    private boolean publishRoleAssignedEvent(User user, TypeRole oldRole, TypeRole newRole, String assignedBy) {
        try {
            Person person = user.getPerson();
            if (person == null || person.getEmail() == null) {
                logger.warn("Cannot send notification - no email address for user: {}", user.getUsername());
                return false;
            }

            UserRoleAssignedEvent event = new UserRoleAssignedEvent(
                    user.getId(),
                    user.getUsername(),
                    person.getEmail(),
                    person.getFirstName(),
                    person.getLastName(),
                    oldRole != null ? oldRole.name() : "NONE",
                    newRole.name(),
                    oldRole == TypeRole.NEW_USER && newRole != TypeRole.NEW_USER,
                    assignedBy,
                    LocalDateTime.now()
            );

            // Publish event to Kafka; the consumer sends the email.
            kafkaEventProducer.publishUserRoleAssignedEvent(event);
            logger.info("Published UserRoleAssignedEvent to Kafka for user: {}", user.getUsername());
            return true;

        } catch (Exception e) {
            logger.error("Failed to send notification for user: {}: {}",
                    user.getUsername(), e.getMessage());
            return false;
        }
    }

    private TypeRole parseSetupRole(String roleStr) {
        TypeRole role;
        try {
            role = TypeRole.valueOf(String.valueOf(roleStr).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid role. Valid setup roles are: EMPLOYEE, TEAM_LEADER, HR_MANAGER");
        }
        if (role == TypeRole.NEW_USER) {
            throw new BadRequestException("NEW_USER is not a valid target role for setup.");
        }
        return role;
    }

    private Department requireSetupDepartment(Long departmentId) {
        if (departmentId == null) {
            throw new BadRequestException("Department is required.");
        }
        return departmentService.requireDepartmentForEmployment(departmentId);
    }

    private JobTitle requireSetupJobTitle(Long jobTitleId) {
        if (jobTitleId == null) {
            throw new BadRequestException("Job title is required.");
        }
        return jobTitleService.requireJobTitleForEmployment(jobTitleId);
    }

    private void validateRoleSpecificSetup(User user, TypeRole role, CompleteUserSetupRequest request) {
        if (role == TypeRole.EMPLOYEE) {
            if (request.getTeamId() == null) {
                throw new BadRequestException("Team is required for EMPLOYEE setup.");
            }
            if (request.getLedTeamId() != null) {
                throw new BadRequestException("EMPLOYEE setup must not include ledTeamId.");
            }
            if (teamRepository.existsByTeamLeader(user)) {
                throw new BadRequestException("This user currently leads a team. Reassign or remove team leadership before assigning EMPLOYEE.");
            }
            return;
        }

        if (role == TypeRole.TEAM_LEADER) {
            if (request.getTeamId() != null) {
                throw new BadRequestException("TEAM_LEADER setup must not include teamId.");
            }
            if (user.getTeam() != null) {
                throw new BadRequestException("This user is a regular team member. Remove team membership before assigning TEAM_LEADER.");
            }
            return;
        }

        if (role == TypeRole.HR_MANAGER) {
            if (request.getTeamId() != null || request.getLedTeamId() != null) {
                throw new BadRequestException("HR_MANAGER setup must not include teamId or ledTeamId.");
            }
            if (teamRepository.existsByTeamLeader(user)) {
                throw new BadRequestException("This user currently leads a team. Reassign or remove team leadership before assigning HR_MANAGER.");
            }
            if (user.getTeam() != null) {
                throw new BadRequestException("This user is a regular team member. Remove team membership before assigning HR_MANAGER.");
            }
        }
    }

    private Team loadTeamRequiredForEmployee(Long teamId) {
        Team team = teamRepository.findByIdWithDetails(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));
        if (team.getTeamLeader() == null) {
            throw new BadRequestException("Target team must have a Team Leader before assigning members.");
        }
        return team;
    }

    private Team loadTeamRequiredForLeadership(Long teamId) {
        return teamRepository.findByIdWithDetails(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));
    }

    private void validateLeadershipTarget(User user, Team ledTeam) {
        User currentLeader = ledTeam.getTeamLeader();
        if (currentLeader != null && !Objects.equals(currentLeader.getId(), user.getId())) {
            throw new BadRequestException("This team already has a Team Leader. Use Team Management to change the leader.");
        }

        teamRepository.findByTeamLeader(user)
                .filter(existing -> !Objects.equals(existing.getId(), ledTeam.getId()))
                .ifPresent(existing -> {
                    throw new BadRequestException("This Team Leader is already assigned to another team.");
                });
    }

    private void touchTeam(Team team) {
        if (team == null) {
            return;
        }
        team.setUpdatedAt(LocalDateTime.now());
        teamRepository.save(team);
    }

    /**
     * Deactivate a user account.
     * Business rules:
     * - HR Manager cannot deactivate their own account.
     * - An already deactivated account returns a clear message.
     */
    public Map<String, Object> deactivateUser(Long userId, String deactivatedBy) {
        return deactivateUser(userId, null, deactivatedBy);
    }

    public Map<String, Object> deactivateUser(Long userId, String deactivatedByKeycloakId, String deactivatedByUsername) {
        User user = userService.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        if (isSameActor(user, deactivatedByKeycloakId, deactivatedByUsername)) {
            throw new BadRequestException("You cannot change your own account activation status.");
        }

        if (Boolean.FALSE.equals(user.getActive())) {
            throw new BadRequestException("User '" + user.getUsername() + "' is already deactivated.");
        }

        String keycloakUserId = user.getKeycloakId();
        if (keycloakUserId == null || keycloakUserId.isBlank()) {
            throw new BadRequestException("User has no Keycloak ID. Cannot deactivate safely.");
        }

        boolean keycloakDisabled = keycloakAdminService.setUserEnabled(keycloakUserId, false);
        if (!keycloakDisabled) {
            throw new BadRequestException("Failed to disable user in Keycloak. Local account was not changed.");
        }

        user.setActive(false);
        userService.saveUser(user);
        logger.info("User '{}' deactivated by '{}'", user.getUsername(), actorLabel(deactivatedByKeycloakId, deactivatedByUsername));

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "User '" + user.getUsername() + "' has been deactivated.");
        response.put("userId", userId);
        response.put("username", user.getUsername());
        response.put("active", false);
        response.put("deactivatedBy", actorLabel(deactivatedByKeycloakId, deactivatedByUsername));
        return response;
    }

    /**
     * Reactivate a previously deactivated user account.
     */
    public Map<String, Object> activateUser(Long userId, String activatedBy) {
        return activateUser(userId, null, activatedBy);
    }

    public Map<String, Object> activateUser(Long userId, String activatedByKeycloakId, String activatedByUsername) {
        User user = userService.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        if (isSameActor(user, activatedByKeycloakId, activatedByUsername)) {
            throw new BadRequestException("You cannot change your own account activation status.");
        }

        if (Boolean.TRUE.equals(user.getActive())) {
            throw new BadRequestException("User '" + user.getUsername() + "' is already active.");
        }

        String keycloakUserId = user.getKeycloakId();
        if (keycloakUserId == null || keycloakUserId.isBlank()) {
            throw new BadRequestException("User has no Keycloak ID. Cannot reactivate safely.");
        }

        boolean keycloakEnabled = keycloakAdminService.setUserEnabled(keycloakUserId, true);
        if (!keycloakEnabled) {
            throw new BadRequestException("Failed to enable user in Keycloak. Local account was not changed.");
        }

        user.setActive(true);
        userService.saveUser(user);
        logger.info("User '{}' reactivated by '{}'", user.getUsername(), actorLabel(activatedByKeycloakId, activatedByUsername));

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "User '" + user.getUsername() + "' has been reactivated.");
        response.put("userId", userId);
        response.put("username", user.getUsername());
        response.put("active", true);
        response.put("activatedBy", actorLabel(activatedByKeycloakId, activatedByUsername));
        return response;
    }

    private boolean isSameActor(User target, String actorKeycloakId, String actorUsername) {
        if (target == null) {
            return false;
        }
        if (actorKeycloakId != null && !actorKeycloakId.isBlank()
                && target.getKeycloakId() != null && target.getKeycloakId().equals(actorKeycloakId)) {
            return true;
        }
        return actorUsername != null && !actorUsername.isBlank()
                && target.getUsername() != null
                && target.getUsername().equalsIgnoreCase(actorUsername);
    }

    private String actorLabel(String actorKeycloakId, String actorUsername) {
        return actorUsername != null && !actorUsername.isBlank() ? actorUsername : actorKeycloakId;
    }

    private Map<String, Object> mapUserToDetails(User user) {
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("keycloakId", user.getKeycloakId());
        userInfo.put("registrationDate", user.getRegistrationDate());
        userInfo.put("active", user.getActive());
        userInfo.put("emailVerified", user.getEmailVerified());
        userInfo.put("role", user.getRole());

        if (user.getPerson() != null) {
            Person person = user.getPerson();
            // Use HashMap instead of Map.of() - Map.of() throws NullPointerException on null values
            Map<String, Object> personalInfo = new HashMap<>();
            personalInfo.put("firstName",       person.getFirstName()  != null ? person.getFirstName()  : "");
            personalInfo.put("lastName",        person.getLastName()   != null ? person.getLastName()   : "");
            personalInfo.put("email",           person.getEmail()      != null ? person.getEmail()      : "");
            personalInfo.put("phone",           person.getPhone()      != null ? person.getPhone()      : "");
            personalInfo.put("address",         person.getAddress()    != null ? person.getAddress()    : "");
            personalInfo.put("birthDate",       person.getBirthDate()  != null ? person.getBirthDate().toString() : "");
            personalInfo.put("maritalStatus",   person.getMaritalStatus()   != null ? person.getMaritalStatus()   : "");
            personalInfo.put("numberOfChildren",person.getNumberOfChildren());
            personalInfo.put("departmentId",    person.getDepartmentId());
            personalInfo.put("department",      person.getDepartment() != null ? person.getDepartment() : "");
            personalInfo.put("departmentDescription", person.getDepartmentDescription() != null ? person.getDepartmentDescription() : "");
            personalInfo.put("departmentActive", person.getDepartmentActive());
            personalInfo.put("jobTitleId",    person.getJobTitleId());
            personalInfo.put("jobTitle",        person.getJobTitle()   != null ? person.getJobTitle()   : "");
            personalInfo.put("jobTitleDescription", person.getJobTitleDescription() != null ? person.getJobTitleDescription() : "");
            personalInfo.put("jobTitleActive",  person.getJobTitleActive());
            personalInfo.put("salary",          person.getSalary());
            personalInfo.put("hireDate",        person.getHireDate()   != null ? person.getHireDate().toString()  : "");
            personalInfo.put("avatarPhoto",     person.getAvatarPhoto() != null ? person.getAvatarPhoto() : "");
            personalInfo.put("avatarColor",     person.getAvatarColor() != null ? person.getAvatarColor() : "");
            userInfo.put("personalInfo", personalInfo);
        }

        Map<String, Object> requiredDocuments = buildRequiredDocuments(user);
        Map<String, Object> teamInfo = buildTeamInfo(user);
        List<String> setupIssues = buildSetupIssues(user, requiredDocuments, teamInfo);

        userInfo.put("teamInfo", teamInfo);
        userInfo.put("requiredDocuments", requiredDocuments);
        userInfo.put("setupStatus", resolveSetupStatus(user, setupIssues));
        userInfo.put("setupIssues", setupIssues);

        return userInfo;
    }

    private Map<String, Object> buildTeamInfo(User user) {
        if (user == null) {
            return null;
        }

        if (user.getRole() == TypeRole.TEAM_LEADER) {
            Optional<Team> ledTeam = teamRepository.findByTeamLeader(user);
            if (ledTeam.isPresent()) {
                return buildTeamInfoMap(ledTeam.get(), "Team Leader", user);
            }
        }

        if (user.getRole() == TypeRole.EMPLOYEE && user.getTeam() != null) {
            return buildTeamInfoMap(user.getTeam(), "Member", user.getTeam().getTeamLeader());
        }

        return null;
    }

    private Map<String, Object> buildTeamInfoMap(Team team, String teamRole, User leader) {
        Map<String, Object> teamInfo = new HashMap<>();
        teamInfo.put("teamId", team.getId());
        teamInfo.put("teamName", team.getName());
        teamInfo.put("teamRole", teamRole);
        if (leader != null) {
            teamInfo.put("teamLeaderId", leader.getId());
            teamInfo.put("teamLeaderName", displayName(leader));
        }
        return teamInfo;
    }

    private String displayName(User user) {
        if (user == null) {
            return "";
        }
        Person person = user.getPerson();
        if (person != null) {
            String firstName = person.getFirstName() != null ? person.getFirstName().trim() : "";
            String lastName = person.getLastName() != null ? person.getLastName().trim() : "";
            String fullName = (firstName + " " + lastName).trim();
            if (!fullName.isBlank()) {
                return fullName;
            }
        }
        return user.getUsername();
    }

    private List<String> buildSetupIssues(User user, Map<String, Object> requiredDocuments, Map<String, Object> teamInfo) {
        if (user == null || user.getRole() == TypeRole.NEW_USER) {
            return List.of();
        }

        List<String> issues = new ArrayList<>();
        Person person = user.getPerson();

        if (person == null || person.getDepartmentId() == null) {
            issues.add("MISSING_DEPARTMENT");
        }
        if (person == null || person.getJobTitleId() == null) {
            issues.add("MISSING_JOB_TITLE");
        }
        if (person == null || person.getHireDate() == null) {
            issues.add("MISSING_HIRE_DATE");
        }

        if (user.getRole() == TypeRole.EMPLOYEE && teamInfo == null) {
            issues.add("MISSING_TEAM");
        }
        if ((user.getRole() == TypeRole.EMPLOYEE || user.getRole() == TypeRole.TEAM_LEADER)
                && isMissingRequiredContractCopy(requiredDocuments)) {
            issues.add("MISSING_CONTRACT_COPY");
        }

        return issues;
    }

    @SuppressWarnings("unchecked")
    private boolean isMissingRequiredContractCopy(Map<String, Object> requiredDocuments) {
        Object contractCopyValue = requiredDocuments.get("contractCopy");
        if (!(contractCopyValue instanceof Map<?, ?> contractCopy)) {
            return false;
        }
        return Boolean.TRUE.equals(contractCopy.get("required"))
                && !Boolean.TRUE.equals(contractCopy.get("uploaded"));
    }

    private String resolveSetupStatus(User user, List<String> setupIssues) {
        if (user != null && user.getRole() == TypeRole.NEW_USER) {
            return "PENDING_ROLE";
        }
        return setupIssues.isEmpty() ? "COMPLETE" : "INCOMPLETE";
    }

    private Map<String, Object> buildRequiredDocuments(User user) {
        Map<String, Object> required = new HashMap<>();
        boolean applies = user.getRole() == TypeRole.EMPLOYEE || user.getRole() == TypeRole.TEAM_LEADER;
        Map<String, Object> contractCopy = new HashMap<>();
        contractCopy.put("required", applies);
        contractCopy.put("uploaded", false);

        if (applies) {
            Optional<StoredEmployeeDocument> existing = storedDocumentRepository
                    .findFirstByEmployeeAndDocumentTypeAndActiveTrueOrderByUploadedAtDesc(user, DocumentType.CONTRACT_COPY);
            existing.ifPresent(doc -> {
                contractCopy.put("uploaded", true);
                contractCopy.put("documentId", doc.getId());
                contractCopy.put("fileName", doc.getFileName());
                contractCopy.put("uploadedAt", doc.getUploadedAt());
                contractCopy.put("uploadedBy", doc.getUploadedBy());
            });
        }

        required.put("contractCopy", contractCopy);
        return required;
    }
}
