package tn.isetbizerte.pfe.hrbackend.modules.hr.service;

import org.springframework.stereotype.Service;
import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;
import tn.isetbizerte.pfe.hrbackend.common.exception.ResourceNotFoundException;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.Person;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.LoginHistory;
import tn.isetbizerte.pfe.hrbackend.modules.user.service.UserService;

import java.util.*;
import java.util.stream.Collectors;


@Service
public class HRService {

    private final UserService userService;

    public HRService(UserService userService) {
        this.userService = userService;
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
    }
}

