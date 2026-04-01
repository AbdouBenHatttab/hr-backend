package tn.isetbizerte.pfe.hrbackend.modules.hr.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import tn.isetbizerte.pfe.hrbackend.modules.hr.dto.AssignRoleRequest;
import tn.isetbizerte.pfe.hrbackend.modules.hr.dto.AssignRoleResponse;
import tn.isetbizerte.pfe.hrbackend.modules.hr.service.HRService;

import java.util.*;

@RestController
@RequestMapping("/api/hr")
public class HRController {

    private final HRService hrService;

    public HRController(HRService hrService) {
        this.hrService = hrService;
    }

    /**
     * Get dashboard overview for HR Manager
     */
    @GetMapping("/dashboard")
    public Map<String, Object> getDashboard(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> response = new HashMap<>();

        response.put("message", "HR Manager Dashboard");
        response.put("hrManagerUsername", jwt.getClaimAsString("preferred_username"));
        response.put("statistics", hrService.getDashboardStatistics());

        return response;
    }

    /**
     * Get all users with their personal information
     */
    @GetMapping("/users")
    public Map<String, Object> getAllUsers(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Map<String, Object> response = new HashMap<>();

        Pageable pageable = PageRequest.of(page, size);
        Page<Map<String, Object>> users = hrService.getAllUsersWithDetails(pageable);

        response.put("message", "All users retrieved successfully");
        response.put("requestedBy", jwt.getClaimAsString("preferred_username"));
        response.put("totalCount", users.getTotalElements());
        response.put("totalPages", users.getTotalPages());
        response.put("page", page);
        response.put("size", size);
        response.put("users", users.getContent());

        return response;
    }

    /**
     * Get all login history (simplified: date/time, id, username only)
     */
    @GetMapping("/login-history")
    public Map<String, Object> getLoginHistory(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Map<String, Object> response = new HashMap<>();

        Pageable pageable = PageRequest.of(page, size);
        Page<Map<String, Object>> historyList = hrService.getLoginHistory(pageable);

        response.put("message", "Login history retrieved successfully");
        response.put("requestedBy", jwt.getClaimAsString("preferred_username"));
        response.put("totalCount", historyList.getTotalElements());
        response.put("totalPages", historyList.getTotalPages());
        response.put("page", page);
        response.put("size", size);
        response.put("loginHistory", historyList.getContent());

        return response;
    }

    /**
     * Get users waiting for role assignment (NEW_USER role only)
     */
    @GetMapping("/pending-approvals")
    public Map<String, Object> getPendingApprovals(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Map<String, Object> response = new HashMap<>();

        Pageable pageable = PageRequest.of(page, size);
        Page<Map<String, Object>> pendingUsers = hrService.getPendingApprovals(pageable);

        response.put("message", "Pending role assignments");
        response.put("note", "These users have NEW_USER role and are waiting for HR Manager to assign EMPLOYEE or TEAM_LEADER role");
        response.put("requestedBy", jwt.getClaimAsString("preferred_username"));
        response.put("instructions", "Use POST /api/hr/assign-role endpoint to assign roles");
        response.put("totalCount", pendingUsers.getTotalElements());
        response.put("totalPages", pendingUsers.getTotalPages());
        response.put("page", page);
        response.put("size", size);
        response.put("pendingUsers", pendingUsers.getContent());

        return response;
    }

    /**
     * Assign role to a user (HR Manager only)
     *
     * POST /api/hr/assign-role
     * Body: {"userId":123,"role":"EMPLOYEE"}
     */
    @PostMapping("/assign-role")
    public ResponseEntity<AssignRoleResponse> assignRoleToUser(
            @RequestBody AssignRoleRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        String assignedBy = jwt.getClaimAsString("preferred_username");

        AssignRoleResponse response = hrService.assignRoleToUser(
                request.getUserId(),
                request.getRole(),
                assignedBy
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Deactivate a user account (HR Manager only)
     * PATCH /api/hr/users/{userId}/deactivate
     */
    @PatchMapping("/users/{userId}/deactivate")
    public ResponseEntity<Map<String, Object>> deactivateUser(
            @PathVariable Long userId,
            @AuthenticationPrincipal Jwt jwt) {

        String deactivatedBy = jwt.getClaimAsString("preferred_username");
        Map<String, Object> response = hrService.deactivateUser(userId, deactivatedBy);
        return ResponseEntity.ok(response);
    }

    /**
     * Reactivate a user account (HR Manager only)
     * PATCH /api/hr/users/{userId}/activate
     */
    @PatchMapping("/users/{userId}/activate")
    public ResponseEntity<Map<String, Object>> activateUser(
            @PathVariable Long userId,
            @AuthenticationPrincipal Jwt jwt) {

        String activatedBy = jwt.getClaimAsString("preferred_username");
        Map<String, Object> response = hrService.activateUser(userId, activatedBy);
        return ResponseEntity.ok(response);
    }
}
