package tn.isetbizerte.pfe.hrbackend.modules.task.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import tn.isetbizerte.pfe.hrbackend.modules.task.dto.CreateProjectRequest;
import tn.isetbizerte.pfe.hrbackend.modules.task.dto.CreateTaskRequest;
import tn.isetbizerte.pfe.hrbackend.modules.task.dto.UpdateTaskStatusRequest;
import tn.isetbizerte.pfe.hrbackend.modules.task.service.TaskService;

import java.util.HashMap;
import java.util.Map;

@RestController
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    // ─────────────────────────────────────────────────────────────
    // TEAM LEADER — PROJECTS
    // ─────────────────────────────────────────────────────────────

    /** GET /api/leader/projects — get all projects for my team */
    @PreAuthorize("hasRole('TEAM_LEADER')")
    @GetMapping("/api/leader/projects")
    public ResponseEntity<Map<String, Object>> getMyProjects(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("data", taskService.getMyProjects(jwt.getSubject()));
        return ResponseEntity.ok(res);
    }

    /** POST /api/leader/projects — create a project */
    @PreAuthorize("hasRole('TEAM_LEADER')")
    @PostMapping("/api/leader/projects")
    public ResponseEntity<Map<String, Object>> createProject(
            @Valid @RequestBody CreateProjectRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("message", "Project created");
        res.put("data", taskService.createProject(jwt.getSubject(), req));
        return ResponseEntity.ok(res);
    }

    /** DELETE /api/leader/projects/{projectId} — delete a project */
    @PreAuthorize("hasRole('TEAM_LEADER')")
    @DeleteMapping("/api/leader/projects/{projectId}")
    public ResponseEntity<Map<String, Object>> deleteProject(
            @PathVariable Long projectId,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(taskService.deleteProject(jwt.getSubject(), projectId));
    }

    // ─────────────────────────────────────────────────────────────
    // TEAM LEADER — TASKS
    // ─────────────────────────────────────────────────────────────

    /** POST /api/leader/projects/{projectId}/tasks — create a task inside a project */
    @PreAuthorize("hasRole('TEAM_LEADER')")
    @PostMapping("/api/leader/projects/{projectId}/tasks")
    public ResponseEntity<Map<String, Object>> createTask(
            @PathVariable Long projectId,
            @Valid @RequestBody CreateTaskRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("message", "Task created");
        res.put("data", taskService.createTask(jwt.getSubject(), projectId, req));
        return ResponseEntity.ok(res);
    }

    /** DELETE /api/leader/tasks/{taskId} — delete a task */
    @PreAuthorize("hasRole('TEAM_LEADER')")
    @DeleteMapping("/api/leader/tasks/{taskId}")
    public ResponseEntity<Map<String, Object>> deleteTask(
            @PathVariable Long taskId,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(taskService.deleteTask(jwt.getSubject(), taskId));
    }

    // ─────────────────────────────────────────────────────────────
    // EMPLOYEE — view and update own tasks
    // ─────────────────────────────────────────────────────────────

    /** GET /api/employee/tasks — get my assigned tasks */
    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER')")
    @GetMapping("/api/employee/tasks")
    public ResponseEntity<Map<String, Object>> getMyTasks(@AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("data", taskService.getMyTasks(username));
        return ResponseEntity.ok(res);
    }

    /** PATCH /api/employee/tasks/{taskId}/status — employee updates own task status */
    @PreAuthorize("hasAnyRole('EMPLOYEE','TEAM_LEADER')")
    @PatchMapping("/api/employee/tasks/{taskId}/status")
    public ResponseEntity<Map<String, Object>> updateTaskStatus(
            @PathVariable Long taskId,
            @Valid @RequestBody UpdateTaskStatusRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("data", taskService.updateTaskStatus(username, taskId, req.getStatus()));
        return ResponseEntity.ok(res);
    }
}
