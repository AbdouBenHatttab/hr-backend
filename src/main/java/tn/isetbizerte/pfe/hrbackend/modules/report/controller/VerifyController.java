package tn.isetbizerte.pfe.hrbackend.modules.report.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.isetbizerte.pfe.hrbackend.modules.employee.entity.LeaveRequest;
import tn.isetbizerte.pfe.hrbackend.modules.employee.repository.LeaveRequestRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Public endpoint — no authentication required.
 * Anyone who scans the QR code on a PDF can verify the document.
 *
 * GET /public/verify/{token}
 */
@RestController
@RequestMapping("/public/verify")
public class VerifyController {

    private final LeaveRequestRepository leaveRequestRepository;

    public VerifyController(LeaveRequestRepository leaveRequestRepository) {
        this.leaveRequestRepository = leaveRequestRepository;
    }

    @GetMapping("/{token}")
    public ResponseEntity<Map<String, Object>> verifyDocument(@PathVariable String token) {
        Optional<LeaveRequest> result = leaveRequestRepository.findByVerificationToken(token);

        if (result.isEmpty()) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("valid", false);
            error.put("message", "Document not found or token is invalid.");
            return ResponseEntity.status(404).body(error);
        }

        LeaveRequest leave = result.get();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("valid", true);
        response.put("employee",     leave.getEmployeeFullName());
        response.put("email",        leave.getEmployeeEmail());
        response.put("leaveType",    leave.getLeaveType().name());
        response.put("startDate",    leave.getStartDate().toString());
        response.put("endDate",      leave.getEndDate().toString());
        response.put("numberOfDays", leave.getNumberOfDays());
        response.put("status",       leave.getStatus().name());
        response.put("approvalDate", leave.getApprovalDate() != null ? leave.getApprovalDate().toString() : "N/A");
        response.put("message",      "✅ This document is authentic and was issued by HR Nexus.");

        return ResponseEntity.ok(response);
    }
}
