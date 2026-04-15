package tn.isetbizerte.pfe.hrbackend.modules.hr.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;
import tn.isetbizerte.pfe.hrbackend.common.event.UserRoleAssignedEvent;
import tn.isetbizerte.pfe.hrbackend.common.exception.BadRequestException;
import tn.isetbizerte.pfe.hrbackend.common.exception.ResourceNotFoundException;
import tn.isetbizerte.pfe.hrbackend.infrastructure.kafka.producer.KafkaEventProducer;
import tn.isetbizerte.pfe.hrbackend.modules.hr.dto.AssignRoleResponse;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.Person;
import tn.isetbizerte.pfe.hrbackend.modules.user.service.UserService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


@Service
public class HRService {

    private static final Logger logger = LoggerFactory.getLogger(HRService.class);

    private final UserService userService;
    private final KeycloakAdminService keycloakAdminService;
    private final KafkaEventProducer kafkaEventProducer;

    public HRService(UserService userService,
                     KeycloakAdminService keycloakAdminService,
                     KafkaEventProducer kafkaEventProducer) {
        this.userService = userService;
        this.keycloakAdminService = keycloakAdminService;
        this.kafkaEventProducer = kafkaEventProducer;
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

        // Find user
        User user = userService.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        TypeRole oldRole = user.getRole();

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

        // Auto-fill employment defaults when the user receives a real role.
        // Do not overwrite HR-entered values.
        if (newRole != TypeRole.NEW_USER && user.getPerson() != null) {
            Person p = user.getPerson();
            if (p.getHireDate() == null) {
                p.setHireDate(LocalDate.now());
            }
            if (p.getSalary() == null) {
                p.setSalary(defaultSalaryForRole(newRole));
            }
            userService.savePerson(p);
        }

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
                    assignedBy,
                    LocalDateTime.now()
            );

            // Publish event to Kafka → Consumer will send email
            kafkaEventProducer.publishUserRoleAssignedEvent(event);
            logger.info("Published UserRoleAssignedEvent to Kafka for user: {}", user.getUsername());
            return true;

        } catch (Exception e) {
            logger.error("Failed to send notification for user: {}: {}",
                    user.getUsername(), e.getMessage());
            return false;
        }
    }

    private BigDecimal defaultSalaryForRole(TypeRole role) {
        if (role == null) return BigDecimal.ZERO;
        return switch (role) {
            case EMPLOYEE -> new BigDecimal("2000");
            case TEAM_LEADER -> new BigDecimal("4000");
            case HR_MANAGER -> new BigDecimal("3500");
            case NEW_USER -> BigDecimal.ZERO;
        };
    }

    /**
     * Deactivate a user account.
     * Business rules:
     * - HR Manager cannot deactivate their own account.
     * - An already deactivated account returns a clear message.
     */
    public Map<String, Object> deactivateUser(Long userId, String deactivatedBy) {
        User user = userService.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        if (user.getUsername().equals(deactivatedBy)) {
            throw new BadRequestException("You cannot deactivate your own account.");
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
        logger.info("User '{}' deactivated by '{}'", user.getUsername(), deactivatedBy);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "User '" + user.getUsername() + "' has been deactivated.");
        response.put("userId", userId);
        response.put("username", user.getUsername());
        response.put("active", false);
        response.put("deactivatedBy", deactivatedBy);
        return response;
    }

    /**
     * Reactivate a previously deactivated user account.
     */
    public Map<String, Object> activateUser(Long userId, String activatedBy) {
        User user = userService.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

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
        logger.info("User '{}' reactivated by '{}'", user.getUsername(), activatedBy);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "User '" + user.getUsername() + "' has been reactivated.");
        response.put("userId", userId);
        response.put("username", user.getUsername());
        response.put("active", true);
        response.put("activatedBy", activatedBy);
        return response;
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
            // Use HashMap instead of Map.of() — Map.of() throws NullPointerException on null values
            Map<String, Object> personalInfo = new HashMap<>();
            personalInfo.put("firstName",       person.getFirstName()  != null ? person.getFirstName()  : "");
            personalInfo.put("lastName",        person.getLastName()   != null ? person.getLastName()   : "");
            personalInfo.put("email",           person.getEmail()      != null ? person.getEmail()      : "");
            personalInfo.put("phone",           person.getPhone()      != null ? person.getPhone()      : "");
            personalInfo.put("address",         person.getAddress()    != null ? person.getAddress()    : "");
            personalInfo.put("birthDate",       person.getBirthDate()  != null ? person.getBirthDate().toString() : "");
            personalInfo.put("maritalStatus",   person.getMaritalStatus()   != null ? person.getMaritalStatus()   : "");
            personalInfo.put("numberOfChildren",person.getNumberOfChildren());
            personalInfo.put("department",      person.getDepartment() != null ? person.getDepartment() : "");
            personalInfo.put("jobTitle",        person.getJobTitle()   != null ? person.getJobTitle()   : "");
            personalInfo.put("salary",          person.getSalary());
            personalInfo.put("hireDate",        person.getHireDate()   != null ? person.getHireDate().toString()  : "");
            personalInfo.put("avatarPhoto",     person.getAvatarPhoto() != null ? person.getAvatarPhoto() : "");
            personalInfo.put("avatarColor",     person.getAvatarColor() != null ? person.getAvatarColor() : "");
            userInfo.put("personalInfo", personalInfo);
        }

        return userInfo;
    }
}
