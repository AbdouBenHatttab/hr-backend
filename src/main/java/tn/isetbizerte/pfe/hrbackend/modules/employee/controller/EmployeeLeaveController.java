package tn.isetbizerte.pfe.hrbackend.modules.employee.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import tn.isetbizerte.pfe.hrbackend.modules.employee.dto.CreateLeaveRequestDto;
import tn.isetbizerte.pfe.hrbackend.modules.employee.dto.LeaveRequestResponseDto;
import tn.isetbizerte.pfe.hrbackend.modules.employee.service.EmployeeLeaveService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/employee/leave")
public class EmployeeLeaveController {

    private final EmployeeLeaveService employeeLeaveService;

    public EmployeeLeaveController(EmployeeLeaveService employeeLeaveService) {
        this.employeeLeaveService = employeeLeaveService;
    }

    /**
     * Submit a new leave request
     * POST /api/employee/leave/request
     * Both EMPLOYEE and TEAM_LEADER can submit leave requests.
     * Team Leader's requests go directly to HR (no TL approval step needed).
     */
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'TEAM_LEADER')")
    @PostMapping("/request")
    public ResponseEntity<Map<String, Object>> createLeaveRequest(
            @Valid @RequestBody CreateLeaveRequestDto request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String username = jwt.getClaimAsString("preferred_username");
        LeaveRequestResponseDto leaveResponse = employeeLeaveService.createLeaveRequest(username, request);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Leave request submitted successfully");
        response.put("data", leaveResponse);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get all my leave requests
     * GET /api/employee/leave/my-requests
     * Both EMPLOYEE and TEAM_LEADER can view their own requests.
     */
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'TEAM_LEADER')")
    @GetMapping("/my-requests")
    public ResponseEntity<Map<String, Object>> getMyLeaveRequests(
            @AuthenticationPrincipal Jwt jwt
    ) {
        String username = jwt.getClaimAsString("preferred_username");
        List<LeaveRequestResponseDto> leaves = employeeLeaveService.getMyLeaveRequests(username);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Your leave requests retrieved successfully");
        response.put("count", leaves.size());
        response.put("data", leaves);

        return ResponseEntity.ok(response);
    }

    /**
     * Get a specific leave request by ID
     * GET /api/employee/leave/{leaveId}
     * NOTE: keep this AFTER all fixed-path GETs (/pending, /all, /my-requests)
     * so Spring matches those first before trying to parse {leaveId}.
     */
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'TEAM_LEADER')")
    @GetMapping("/{leaveId}")
    public ResponseEntity<Map<String, Object>> getLeaveRequest(@PathVariable Long leaveId) {
        LeaveRequestResponseDto leave = employeeLeaveService.getLeaveRequestById(leaveId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Leave request retrieved successfully");
        response.put("data", leave);

        return ResponseEntity.ok(response);
    }

    /**
     * Get pending leave requests.
     * Team Leader sees ONLY their team's requests.
     * HR Manager sees ALL pending requests.
     * GET /api/employee/leave/pending
     */
    /**
     * Team Leader sees only their team's PENDING requests.
     * HR uses GET /all instead.
     */
    @PreAuthorize("hasRole('TEAM_LEADER')")
    @GetMapping("/pending")
    public ResponseEntity<Map<String, Object>> getPendingLeaveRequests(
            @AuthenticationPrincipal Jwt jwt) {

        List<LeaveRequestResponseDto> leaves =
                employeeLeaveService.getPendingLeaveRequestsForTeamLeader(jwt.getSubject());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Pending leave requests retrieved successfully");
        response.put("count", leaves.size());
        response.put("data", leaves);

        return ResponseEntity.ok(response);
    }

    /**
     * Get all leave requests for Team Leader's team (any status)
     * GET /api/employee/leave/my-team
     */
    @PreAuthorize("hasRole('TEAM_LEADER')")
    @GetMapping("/my-team")
    public ResponseEntity<Map<String, Object>> getTeamLeaveRequests(
            @AuthenticationPrincipal Jwt jwt) {

        String keycloakId = jwt.getSubject();
        List<LeaveRequestResponseDto> leaves =
                employeeLeaveService.getAllLeaveRequestsForTeamLeader(keycloakId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Team leave requests retrieved successfully");
        response.put("count", leaves.size());
        response.put("data", leaves);

        return ResponseEntity.ok(response);
    }

    /**
     * Get ALL leave requests — HR overview
     * GET /api/employee/leave/all
     */
    @PreAuthorize("hasRole('HR_MANAGER')")
    @GetMapping("/all")
    public ResponseEntity<Map<String, Object>> getAllLeaveRequests() {
        List<LeaveRequestResponseDto> leaves = employeeLeaveService.getAllLeaveRequests();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "All leave requests retrieved successfully");
        response.put("count", leaves.size());
        response.put("data", leaves);

        return ResponseEntity.ok(response);
    }

    /**
     * Team Leader approves a leave request — only for their own team members
     * POST /api/employee/leave/{leaveId}/team-leader/approve
     */
    @PreAuthorize("hasRole('TEAM_LEADER')")
    @PostMapping("/{leaveId}/team-leader/approve")
    public ResponseEntity<Map<String, Object>> teamLeaderApprove(
            @PathVariable Long leaveId,
            @AuthenticationPrincipal Jwt jwt) {
        String keycloakId = jwt.getSubject();
        LeaveRequestResponseDto leave = employeeLeaveService.teamLeaderDecision(leaveId, true, keycloakId);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Leave request approved by Team Leader");
        response.put("data", leave);
        return ResponseEntity.ok(response);
    }

    /**
     * Team Leader rejects a leave request — only for their own team members
     * POST /api/employee/leave/{leaveId}/team-leader/reject
     */
    @PreAuthorize("hasRole('TEAM_LEADER')")
    @PostMapping("/{leaveId}/team-leader/reject")
    public ResponseEntity<Map<String, Object>> teamLeaderReject(
            @PathVariable Long leaveId,
            @AuthenticationPrincipal Jwt jwt) {
        String keycloakId = jwt.getSubject();
        LeaveRequestResponseDto leave = employeeLeaveService.teamLeaderDecision(leaveId, false, keycloakId);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Leave request rejected by Team Leader");
        response.put("data", leave);
        return ResponseEntity.ok(response);
    }

    /**
     * HR Manager approves a leave request
     * POST /api/employee/leave/{leaveId}/hr/approve
     */
    @PreAuthorize("hasRole('HR_MANAGER')")
    @PostMapping("/{leaveId}/hr/approve")
    public ResponseEntity<Map<String, Object>> hrApprove(@PathVariable Long leaveId) {
        LeaveRequestResponseDto leave = employeeLeaveService.hrDecision(leaveId, true);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Leave request approved by HR Manager");
        response.put("data", leave);
        return ResponseEntity.ok(response);
    }

    /**
     * HR Manager rejects a leave request
     * POST /api/employee/leave/{leaveId}/hr/reject
     */
    @PreAuthorize("hasRole('HR_MANAGER')")
    @PostMapping("/{leaveId}/hr/reject")
    public ResponseEntity<Map<String, Object>> hrReject(@PathVariable Long leaveId) {
        LeaveRequestResponseDto leave = employeeLeaveService.hrDecision(leaveId, false);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Leave request rejected by HR Manager");
        response.put("data", leave);
        return ResponseEntity.ok(response);
    }
}

