package tn.isetbizerte.pfe.hrbackend.modules.employee.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import tn.isetbizerte.pfe.hrbackend.modules.employee.dto.CreateLeaveRequestDto;
import tn.isetbizerte.pfe.hrbackend.modules.employee.dto.LeaveDecisionRequestDto;
import tn.isetbizerte.pfe.hrbackend.modules.employee.dto.LeaveBalanceDto;
import tn.isetbizerte.pfe.hrbackend.modules.employee.dto.LeaveRequestResponseDto;
import tn.isetbizerte.pfe.hrbackend.modules.employee.service.LeaveBalanceService;
import tn.isetbizerte.pfe.hrbackend.modules.employee.service.EmployeeLeaveService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/employee/leave")
public class EmployeeLeaveController {

    private final EmployeeLeaveService employeeLeaveService;
    private final LeaveBalanceService leaveBalanceService;

    public EmployeeLeaveController(EmployeeLeaveService employeeLeaveService,
                                   LeaveBalanceService leaveBalanceService) {
        this.employeeLeaveService = employeeLeaveService;
        this.leaveBalanceService = leaveBalanceService;
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
     * Get current user's yearly leave balances.
     * GET /api/employee/leave/balances?year=2026
     */
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'TEAM_LEADER')")
    @GetMapping("/balances")
    public ResponseEntity<Map<String, Object>> getMyLeaveBalances(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Integer year
    ) {
        String username = jwt.getClaimAsString("preferred_username");
        List<LeaveBalanceDto> balances = leaveBalanceService.getMyBalances(username, year);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Leave balances retrieved successfully");
        response.put("data", balances);

        return ResponseEntity.ok(response);
    }

    /**
     * Get all my leave requests
     * GET /api/employee/leave/my-requests
     * Both EMPLOYEE and TEAM_LEADER can view their own requests.
     */
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'TEAM_LEADER')")
    @GetMapping("/my-requests")
    public ResponseEntity<Map<String, Object>> getMyLeaveRequests(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        String username = jwt.getClaimAsString("preferred_username");
        Pageable pageable = PageRequest.of(page, size);
        Page<LeaveRequestResponseDto> leaves = employeeLeaveService.getMyLeaveRequests(username, pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Your leave requests retrieved successfully");
        response.put("count", leaves.getNumberOfElements());
        response.put("totalCount", leaves.getTotalElements());
        response.put("totalPages", leaves.getTotalPages());
        response.put("page", page);
        response.put("size", size);
        response.put("data", leaves.getContent());

        return ResponseEntity.ok(response);
    }

    /**
     * Employee cancels their own pending leave request before final processing.
     * POST /api/employee/leave/{leaveId}/cancel
     */
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'TEAM_LEADER')")
    @PostMapping("/{leaveId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelMyLeaveRequest(
            @PathVariable Long leaveId,
            @AuthenticationPrincipal Jwt jwt) {
        LeaveRequestResponseDto leave = employeeLeaveService.cancelMyLeaveRequest(leaveId, jwt.getSubject());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Leave request canceled by employee");
        response.put("data", leave);

        return ResponseEntity.ok(response);
    }

    /**
     * Get a specific leave request by ID
     * GET /api/employee/leave/{leaveId}
     * NOTE: keep this AFTER all fixed-path GETs (/pending, /all, /my-requests)
     * so Spring matches those first before trying to parse {leaveId}.
     */
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'TEAM_LEADER', 'HR_MANAGER')")
    @GetMapping("/{leaveId}")
    public ResponseEntity<Map<String, Object>> getLeaveRequest(
            @PathVariable Long leaveId,
            @AuthenticationPrincipal Jwt jwt) {
        LeaveRequestResponseDto leave = employeeLeaveService.getLeaveRequestById(leaveId, jwt.getSubject());

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
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<LeaveRequestResponseDto> leaves =
                employeeLeaveService.getPendingLeaveRequestsForTeamLeader(jwt.getSubject(), pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Pending leave requests retrieved successfully");
        response.put("count", leaves.getNumberOfElements());
        response.put("totalCount", leaves.getTotalElements());
        response.put("totalPages", leaves.getTotalPages());
        response.put("page", page);
        response.put("size", size);
        response.put("data", leaves.getContent());

        return ResponseEntity.ok(response);
    }

    /**
     * Get all leave requests for Team Leader's team (any status)
     * GET /api/employee/leave/my-team
     */
    @PreAuthorize("hasRole('TEAM_LEADER')")
    @GetMapping("/my-team")
    public ResponseEntity<Map<String, Object>> getTeamLeaveRequests(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        String keycloakId = jwt.getSubject();
        Pageable pageable = PageRequest.of(page, size);
        Page<LeaveRequestResponseDto> leaves =
                employeeLeaveService.getAllLeaveRequestsForTeamLeader(keycloakId, pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Team leave requests retrieved successfully");
        response.put("count", leaves.getNumberOfElements());
        response.put("totalCount", leaves.getTotalElements());
        response.put("totalPages", leaves.getTotalPages());
        response.put("page", page);
        response.put("size", size);
        response.put("data", leaves.getContent());

        return ResponseEntity.ok(response);
    }

    /**
     * Get ALL leave requests — HR overview
     * GET /api/employee/leave/all
     */
    @PreAuthorize("hasRole('HR_MANAGER')")
    @GetMapping("/all")
    public ResponseEntity<Map<String, Object>> getAllLeaveRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<LeaveRequestResponseDto> leaves = employeeLeaveService.getAllLeaveRequests(pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "All leave requests retrieved successfully");
        response.put("count", leaves.getNumberOfElements());
        response.put("totalCount", leaves.getTotalElements());
        response.put("totalPages", leaves.getTotalPages());
        response.put("page", page);
        response.put("size", size);
        response.put("data", leaves.getContent());

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
            @Valid @RequestBody LeaveDecisionRequestDto request,
            @AuthenticationPrincipal Jwt jwt) {
        String keycloakId = jwt.getSubject();
        LeaveRequestResponseDto leave = employeeLeaveService.teamLeaderDecision(leaveId, false, keycloakId, request.getReason());
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
    public ResponseEntity<Map<String, Object>> hrApprove(
            @PathVariable Long leaveId,
            @AuthenticationPrincipal Jwt jwt) {
        LeaveRequestResponseDto leave = employeeLeaveService.hrDecision(leaveId, true, null, jwt.getSubject());
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
    public ResponseEntity<Map<String, Object>> hrReject(
            @PathVariable Long leaveId,
            @Valid @RequestBody LeaveDecisionRequestDto request,
            @AuthenticationPrincipal Jwt jwt) {
        LeaveRequestResponseDto leave = employeeLeaveService.hrDecision(leaveId, false, request.getReason(), jwt.getSubject());
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Leave request rejected by HR Manager");
        response.put("data", leave);
        return ResponseEntity.ok(response);
    }
}
