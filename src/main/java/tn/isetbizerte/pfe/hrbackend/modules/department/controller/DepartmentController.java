package tn.isetbizerte.pfe.hrbackend.modules.department.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.isetbizerte.pfe.hrbackend.modules.department.dto.CreateDepartmentRequest;
import tn.isetbizerte.pfe.hrbackend.modules.department.dto.DepartmentResponse;
import tn.isetbizerte.pfe.hrbackend.modules.department.dto.UpdateDepartmentRequest;
import tn.isetbizerte.pfe.hrbackend.modules.department.service.DepartmentService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/hr/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping("/public/active")
    public Map<String, Object> listActiveDepartmentsForSignup() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("departments", departmentService.listActiveDepartmentsForSignup());
        return response;
    }

    @GetMapping
    public Map<String, Object> listDepartments(@RequestParam(defaultValue = "false") boolean activeOnly) {
        List<DepartmentResponse> departments = departmentService.listDepartments(activeOnly);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("departments", departments);
        return response;
    }

    @PostMapping
    public ResponseEntity<DepartmentResponse> createDepartment(@RequestBody CreateDepartmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(departmentService.createDepartment(request));
    }

    @PutMapping("/{departmentId}")
    public DepartmentResponse updateDepartment(
            @PathVariable Long departmentId,
            @RequestBody UpdateDepartmentRequest request) {
        return departmentService.updateDepartment(departmentId, request);
    }

    @PatchMapping("/{departmentId}/archive")
    public DepartmentResponse archiveDepartment(@PathVariable Long departmentId) {
        return departmentService.archiveDepartment(departmentId);
    }

    @PatchMapping("/{departmentId}/activate")
    public DepartmentResponse activateDepartment(@PathVariable Long departmentId) {
        return departmentService.activateDepartment(departmentId);
    }
}
