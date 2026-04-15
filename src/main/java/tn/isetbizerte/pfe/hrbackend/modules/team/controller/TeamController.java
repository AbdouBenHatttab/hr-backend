package tn.isetbizerte.pfe.hrbackend.modules.team.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import tn.isetbizerte.pfe.hrbackend.modules.team.dto.AddMemberRequest;
import tn.isetbizerte.pfe.hrbackend.modules.team.dto.CreateTeamRequest;
import tn.isetbizerte.pfe.hrbackend.modules.team.service.TeamService;

import java.util.HashMap;
import java.util.Map;

@RestController
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    // ─────────────────────────────────────────────────────────────
    // HR ENDPOINTS  →  /api/hr/teams
    // ─────────────────────────────────────────────────────────────

    /**
     * HR creates a new team and assigns a Team Leader.
     * POST /api/hr/teams
     * Body: { "name": "Backend Team", "description": "...", "teamLeaderId": 2 }
     */
    @PreAuthorize("hasRole('HR_MANAGER')")
    @PostMapping("/api/hr/teams")
    public ResponseEntity<Map<String, Object>> createTeam(
            @Valid @RequestBody CreateTeamRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        Map<String, Object> team = teamService.createTeam(
                request.getName(),
                request.getDescription(),
                request.getTeamLeaderId()
        );

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Team created successfully");
        response.put("createdBy", jwt.getClaimAsString("preferred_username"));
        response.put("data", team);
        return ResponseEntity.ok(response);
    }

    /**
     * HR views all teams.
     * GET /api/hr/teams
     */
    @PreAuthorize("hasRole('HR_MANAGER')")
    @GetMapping("/api/hr/teams")
    public ResponseEntity<Map<String, Object>> getAllTeams(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Map<String, Object> response = new HashMap<>();
        Pageable pageable = PageRequest.of(page, size);
        Page<Map<String, Object>> teams = teamService.getAllTeams(pageable);
        response.put("success", true);
        response.put("message", "All teams retrieved successfully");
        response.put("data", teams.getContent());
        response.put("page", page);
        response.put("size", size);
        response.put("totalCount", teams.getTotalElements());
        response.put("totalPages", teams.getTotalPages());
        return ResponseEntity.ok(response);
    }

    /**
     * HR views a specific team by ID.
     * GET /api/hr/teams/{teamId}
     */
    @PreAuthorize("hasRole('HR_MANAGER')")
    @GetMapping("/api/hr/teams/{teamId}")
    public ResponseEntity<Map<String, Object>> getTeamById(@PathVariable Long teamId) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", teamService.getTeamById(teamId));
        return ResponseEntity.ok(response);
    }

    // ─────────────────────────────────────────────────────────────
    // TEAM LEADER ENDPOINTS  →  /api/leader/team
    // ─────────────────────────────────────────────────────────────

    /**
     * Team Leader views their own team and members.
     * GET /api/leader/team
     */
    @PreAuthorize("hasRole('TEAM_LEADER')")
    @GetMapping("/api/leader/team")
    public ResponseEntity<Map<String, Object>> getMyTeam(@AuthenticationPrincipal Jwt jwt) {
        String keycloakId = jwt.getSubject();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Your team retrieved successfully");
        response.put("data", teamService.getMyTeam(keycloakId));
        return ResponseEntity.ok(response);
    }

    /**
     * Team Leader gets list of employees not yet assigned to any team.
     * GET /api/leader/available-employees
     */
    @PreAuthorize("hasRole('TEAM_LEADER')")
    @GetMapping("/api/leader/available-employees")
    public ResponseEntity<Map<String, Object>> getAvailableEmployees(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", teamService.getAvailableEmployees());
        return ResponseEntity.ok(response);
    }

    /**
     * Team Leader views an employee profile (read-only) to decide whether to add them.
     * Only allowed when the employee is either unassigned OR already in the leader's team.
     * GET /api/leader/employees/{employeeId}
     */
    @PreAuthorize("hasRole('TEAM_LEADER')")
    @GetMapping("/api/leader/employees/{employeeId}")
    public ResponseEntity<Map<String, Object>> getEmployeeProfileForLeader(
            @PathVariable Long employeeId,
            @AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", teamService.getEmployeeProfileForLeader(jwt.getSubject(), employeeId));
        return ResponseEntity.ok(response);
    }

    /**
     * Team Leader adds an employee to their team.
     * POST /api/leader/team/members
     * Body: { "employeeId": 3 }
     */
    @PreAuthorize("hasRole('TEAM_LEADER')")
    @PostMapping("/api/leader/team/members")
    public ResponseEntity<Map<String, Object>> addMember(
            @Valid @RequestBody AddMemberRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        String keycloakId = jwt.getSubject();
        Map<String, Object> result = teamService.addMemberToTeam(keycloakId, request.getEmployeeId());
        return ResponseEntity.ok(result);
    }

    /**
     * Team Leader removes an employee from their team.
     * DELETE /api/leader/team/members/{employeeId}
     */
    @PreAuthorize("hasRole('TEAM_LEADER')")
    @DeleteMapping("/api/leader/team/members/{employeeId}")
    public ResponseEntity<Map<String, Object>> removeMember(
            @PathVariable Long employeeId,
            @AuthenticationPrincipal Jwt jwt) {

        String keycloakId = jwt.getSubject();
        Map<String, Object> result = teamService.removeMemberFromTeam(keycloakId, employeeId);
        return ResponseEntity.ok(result);
    }
}
