package tn.isetbizerte.pfe.hrbackend.modules.hr.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.Person;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.LoginHistory;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.PersonRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.UserRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.repository.LoginHistoryRepository;
import tn.isetbizerte.pfe.hrbackend.modules.user.service.UserService;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/hr")
public class HRController {

    private final UserRepository userRepository;
    private final PersonRepository personRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final UserService userService;

    public HRController(UserRepository userRepository, PersonRepository personRepository,
                       LoginHistoryRepository loginHistoryRepository, UserService userService) {
        this.userRepository = userRepository;
        this.personRepository = personRepository;
        this.loginHistoryRepository = loginHistoryRepository;
        this.userService = userService;
    }

    /**
     * Get dashboard overview for HR Manager
     */
    @GetMapping("/dashboard")
    public Map<String, Object> getDashboard(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> response = new HashMap<>();

        long totalUsers = userRepository.count();
        long totalPersons = personRepository.count();
        long totalLoginAttempts = loginHistoryRepository.count();

        // Count users by role (approximation - roles are in Keycloak)
        List<User> allUsers = userRepository.findAll();
        long newUsers = allUsers.stream()
                .filter(user -> user.getRole() != null && user.getRole().name().equals("NEW_USER"))
                .count();

        response.put("message", "HR Manager Dashboard");
        response.put("hrManagerUsername", jwt.getClaimAsString("preferred_username"));
        response.put("statistics", Map.of(
            "totalUsers", totalUsers,
            "totalPersons", totalPersons,
            "totalLoginAttempts", totalLoginAttempts,
            "pendingApprovals", newUsers
        ));

        return response;
    }

    /**
     * Get all users with their personal information
     */
    @GetMapping("/users")
    public Map<String, Object> getAllUsers(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> response = new HashMap<>();

        List<User> users = userRepository.findAll();
        List<Map<String, Object>> userList = users.stream().map(user -> {
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", user.getId());
            userInfo.put("username", user.getUsername());
            userInfo.put("keycloakId", user.getKeycloakId());
            userInfo.put("registrationDate", user.getRegistrationDate());
            userInfo.put("active", user.getActive());
            userInfo.put("emailVerified", user.getEmailVerified());
            userInfo.put("role", user.getRole());

            // Add person information if available
            if (user.getPerson() != null) {
                Person person = user.getPerson();
                userInfo.put("personalInfo", Map.of(
                    "firstName", person.getFirstName(),
                    "lastName", person.getLastName(),
                    "email", person.getEmail(),
                    "phone", person.getPhone() != null ? person.getPhone() : "Not provided",
                    "address", person.getAddress() != null ? person.getAddress() : "Not provided",
                    "birthDate", person.getBirthDate() != null ? person.getBirthDate().toString() : "Not provided",
                    "maritalStatus", person.getMaritalStatus() != null ? person.getMaritalStatus() : "Not provided",
                    "numberOfChildren", person.getNumberOfChildren()
                ));
            }

            return userInfo;
        }).collect(Collectors.toList());

        response.put("message", "All users retrieved successfully");
        response.put("requestedBy", jwt.getClaimAsString("preferred_username"));
        response.put("totalCount", users.size());
        response.put("users", userList);

        return response;
    }

    /**
     * Get all login history (simplified: date/time, id, username only)
     */
    @GetMapping("/login-history")
    public Map<String, Object> getLoginHistory(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> response = new HashMap<>();

        List<LoginHistory> loginHistories = loginHistoryRepository.findAll();
        List<Map<String, Object>> historyList = loginHistories.stream().map(history -> {
            Map<String, Object> historyInfo = new HashMap<>();
            historyInfo.put("id", history.getId());
            historyInfo.put("loginDate", history.getLoginDate());
            historyInfo.put("userId", history.getUser() != null ? history.getUser().getId() : null);
            historyInfo.put("username", history.getUser() != null ? history.getUser().getUsername() : "Unknown");

            return historyInfo;
        }).collect(Collectors.toList());

        response.put("message", "Login history retrieved successfully");
        response.put("requestedBy", jwt.getClaimAsString("preferred_username"));
        response.put("totalCount", loginHistories.size());
        response.put("loginHistory", historyList);

        return response;
    }

    /**
     * Get users waiting for role assignment (NEW_USER role only)
     */
    @GetMapping("/pending-approvals")
    public Map<String, Object> getPendingApprovals(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> response = new HashMap<>();

        // Filter only users with NEW_USER role (pending approval)
        List<User> allUsers = userRepository.findAll();
        List<Map<String, Object>> pendingUsers = allUsers.stream()
                .filter(user -> user.getRole() != null &&
                        user.getRole().name().equals("NEW_USER"))
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
                }).collect(Collectors.toList());

        response.put("message", "Pending role assignments");
        response.put("note", "These users have NEW_USER role and are waiting for HR Manager to assign EMPLOYEE or TEAM_LEADER role");
        response.put("requestedBy", jwt.getClaimAsString("preferred_username"));
        response.put("instructions", "Use POST /api/hr/assign-role endpoint to assign roles");
        response.put("totalCount", pendingUsers.size());
        response.put("pendingUsers", pendingUsers);

        return response;
    }

    /**
     * Assign role to a user (HR Manager only)
     *
     * POST /api/hr/assign-role
     * Body: {"username":"jane_smith","role":"EMPLOYEE"}
     */
    @PostMapping("/assign-role")
    public Map<String, Object> assignRoleToUser(
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal Jwt jwt) {

        Map<String, Object> response = new HashMap<>();

        try {
            String username = request.get("username");
            String roleStr = request.get("role");

            // Validate inputs
            if (username == null || username.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "Username is required");
                return response;
            }

            if (roleStr == null || roleStr.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "Role is required");
                return response;
            }

            // Validate role exists in enum
            TypeRole newRole;
            try {
                newRole = TypeRole.valueOf(roleStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                response.put("success", false);
                response.put("message", "Invalid role. Valid roles are: EMPLOYEE, TEAM_LEADER, HR_MANAGER, NEW_USER");
                return response;
            }

            // Find user
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (!userOpt.isPresent()) {
                response.put("success", false);
                response.put("message", "User not found: " + username);
                return response;
            }

            User user = userOpt.get();
            TypeRole oldRole = user.getRole();

            // Update role in database
            user.setRole(newRole);
            userRepository.save(user);

            // Prepare success response
            response.put("success", true);
            response.put("message", "Role assigned successfully");
            response.put("username", username);
            response.put("oldRole", oldRole != null ? oldRole.name() : "NONE");
            response.put("newRole", newRole.name());
            response.put("assignedBy", jwt.getClaimAsString("preferred_username"));
            response.put("timestamp", new Date());

            return response;

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error assigning role: " + e.getMessage());
            return response;
        }
    }
}
